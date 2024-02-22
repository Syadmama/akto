package com.akto.usage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.usage.UsageMetricInfoDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.UsageMetricInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.DashboardMode;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

public class UsageMetricHandler {

    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageMetricHandler.class);

    static class CalcReturn {
        int measureEpoch;
        Organization organization;
        List<UsageMetric> usageMetrics = new ArrayList<>();

        public CalcReturn(int measureEpoch, Organization organization, List<UsageMetric> usageMetrics) {
            this.measureEpoch = measureEpoch;
            this.organization = organization;
            this.usageMetrics = usageMetrics;
        }
    }

    private static CalcReturn calcAndSaveUsageMetrics(MetricTypes[] metricTypes, int accountId) {

        CalcReturn calcReturn = new CalcReturn(Context.now(), null, new ArrayList<>());

        try {

            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(
                    AccountSettingsDao.generateFilter());

            // Get organization to which account belongs to
            Organization organization = OrganizationsDao.instance.findOne(
                    Filters.in(Organization.ACCOUNTS, accountId));

            if (organization == null) {
                throw new Exception("Organization not found for account: " + accountId);
            }

            loggerMaker.infoAndAddToDb(String.format("Measuring usage for %s / %d ", organization.getName(), accountId),
                    LogDb.DASHBOARD);

            String organizationId = organization.getId();
            String dashboardVersion = accountSettings.getDashboardVersion();

            for (MetricTypes metricType : metricTypes) {
                UsageMetric usageMetric = createUsageMetric(organizationId, accountId, metricType, dashboardVersion);

                // calculate usage for metric
                UsageMetricCalculator.calculateUsageMetric(usageMetric);

                /*
                 * Save a single usage metric per sync cycle,
                 * until it is synced with akto.
                 */
                usageMetric = UsageMetricsDao.instance.getMCollection().findOneAndReplace(
                        Filters.and(
                                UsageMetricsDao.generateFilter(organizationId, accountId, metricType),
                                Filters.eq(UsageMetric.SYNCED_WITH_AKTO, false),
                                Filters.eq(UsageMetric.SYNC_EPOCH, usageMetric.getSyncEpoch())),
                        usageMetric, new FindOneAndReplaceOptions().upsert(true)
                                .returnDocument(ReturnDocument.AFTER));

                loggerMaker.infoAndAddToDb("Usage metric inserted: " + usageMetric.getId(), LogDb.DASHBOARD);
                calcReturn.usageMetrics.add(usageMetric);
            }

            updateOrgMeteredUsage(organization);

            calcReturn.organization = organization;

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,
                    String.format("Error while measuring usage for account %d. Error: %s", accountId, e.getMessage()),
                    LogDb.DASHBOARD);
        }

        return calcReturn;

    }

    public static UsageMetric createUsageMetric(String organizationId, int accountId, MetricTypes metricType, String dashboardVersion){
        UsageMetricInfo usageMetricInfo = UsageMetricInfoDao.instance.findOne(
                UsageMetricsDao.generateFilter(organizationId, accountId, metricType));

        if (usageMetricInfo == null) {
            usageMetricInfo = new UsageMetricInfo(organizationId, accountId, metricType);
            UsageMetricInfoDao.instance.insertOne(usageMetricInfo);
        }

        int syncEpoch = usageMetricInfo.getSyncEpoch();
        int measureEpoch = usageMetricInfo.getMeasureEpoch();

        // Reset measureEpoch every month
        if (Context.now() - measureEpoch > 2629746) {
            if (syncEpoch > Context.now() - 86400) {
                measureEpoch = Context.now();

                UsageMetricInfoDao.instance.updateOne(
                        UsageMetricsDao.generateFilter(organizationId, accountId, metricType),
                        Updates.set(UsageMetricInfo.MEASURE_EPOCH, measureEpoch));
            }
        }

        String dashboardMode = DashboardMode.getDashboardMode().toString();

        UsageMetric usageMetric = new UsageMetric(
                organizationId, accountId, metricType, syncEpoch, measureEpoch,
                dashboardMode, dashboardVersion);

        return usageMetric;
    }

    public static HashMap<String, FeatureAccess> updateFeatureMapWithLocalUsageMetrics(HashMap<String, FeatureAccess> featureWiseAllowed, String organizationId){

        if (featureWiseAllowed == null) {
            featureWiseAllowed = new HashMap<>();
        }

        // since an org can have multiple accounts, we need to consolidate the usage.
        Map<String, FeatureAccess> consolidatedOrgUsage = UsageMetricsDao.instance.findLatestUsageMetricsForOrganization(organizationId);

        for (Map.Entry<String, FeatureAccess> entry : featureWiseAllowed.entrySet()) {
            String featureLabel = entry.getKey();
            FeatureAccess featureAccess = entry.getValue();

            if (consolidatedOrgUsage.containsKey(featureLabel)) {
                FeatureAccess orgUsage = consolidatedOrgUsage.get(featureLabel);
                featureAccess.setUsage(orgUsage.getUsage());

                if(!featureAccess.checkBooleanOrUnlimited() && featureAccess.getUsage() >= featureAccess.getUsageLimit()) {
                    if(featureAccess.getOverageFirstDetected() == -1){
                        featureAccess.setOverageFirstDetected(orgUsage.getOverageFirstDetected());
                    }
                } else {
                    featureAccess.setOverageFirstDetected(-1);
                }
                featureWiseAllowed.put(featureLabel, featureAccess);
            }
        }
        return featureWiseAllowed;
    }

    private static void updateOrgMeteredUsage(Organization organization) {

        String organizationId = organization.getId();

        HashMap<String, FeatureAccess> featureWiseAllowed = organization.getFeatureWiseAllowed();
        featureWiseAllowed = updateFeatureMapWithLocalUsageMetrics(featureWiseAllowed, organizationId);
        organization.setFeatureWiseAllowed(featureWiseAllowed);

        OrganizationsDao.instance.updateOne(
                Filters.eq(Organization.ID, organization.getId()),
                Updates.set(Organization.FEATURE_WISE_ALLOWED, organization.getFeatureWiseAllowed()));
    }

    public static FeatureAccess calcAndFetchFeatureAccessUsingDeltaUsage(MetricTypes metricType, int accountId, int deltaUsage) {
        FeatureAccess featureAccess = FeatureAccess.fullAccess;

        try {
            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(
                    AccountSettingsDao.generateFilter());

            // Get organization to which account belongs to
            Organization organization = OrganizationsDao.instance.findOne(
                    Filters.in(Organization.ACCOUNTS, accountId));

            if (organization == null) {
                throw new Exception("Organization not found for account: " + accountId);
            }

            featureAccess = UsageMetricUtils.getFeatureAccess(organization, metricType);
            int usageBefore = featureAccess.getUsage();
            int usageAfter = usageBefore + deltaUsage;
            featureAccess.setUsage(usageAfter);
            if (!featureAccess.checkBooleanOrUnlimited() &&
                    featureAccess.getUsage() >= featureAccess.getUsageLimit()) {
                if (featureAccess.getOverageFirstDetected() == -1) {
                    featureAccess.setOverageFirstDetected(Context.now());
                }
            } else {
                featureAccess.setOverageFirstDetected(-1);
            }
            organization.getFeatureWiseAllowed().put(metricType.name(), featureAccess);
            
            String organizationId = organization.getId();
            String dashboardVersion = accountSettings.getDashboardVersion();            
            UsageMetric usageMetric = createUsageMetric(organizationId, accountId, metricType, dashboardVersion);
            usageMetric.setRecordedAt(Context.now());
            usageMetric.setUsage(usageAfter);

            usageMetric = UsageMetricsDao.instance.getMCollection().findOneAndReplace(
                    Filters.and(
                            UsageMetricsDao.generateFilter(organizationId, accountId, metricType),
                            Filters.eq(UsageMetric.SYNCED_WITH_AKTO, false),
                            Filters.eq(UsageMetric.SYNC_EPOCH, usageMetric.getSyncEpoch())),
                    usageMetric, new FindOneAndReplaceOptions().upsert(true)
                            .returnDocument(ReturnDocument.AFTER));

            OrganizationsDao.instance.updateOne(
                    Filters.eq(Organization.ID, organization.getId()),
                    Updates.set(Organization.FEATURE_WISE_ALLOWED, organization.getFeatureWiseAllowed()));                

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while calculating usage limit " + e.toString(), LogDb.DASHBOARD);
        }

        return featureAccess;
    }

    public static void calcAndSyncUsageMetrics(MetricTypes[] metricTypes, int accountId) {

        CalcReturn calcReturn = calcAndSaveUsageMetrics(metricTypes, accountId);

        for (UsageMetric usageMetric : calcReturn.usageMetrics) {
            try {
                UsageMetricUtils.syncUsageMetricWithAkto(usageMetric);
                UsageMetricUtils.syncUsageMetricWithMixpanel(usageMetric);
                loggerMaker.infoAndAddToDb(String.format("Synced usage metric %s  %s/%d %s",
                        usageMetric.getId().toString(), usageMetric.getOrganizationId(),
                        usageMetric.getAccountId(), usageMetric.getMetricType().toString()), LogDb.DASHBOARD);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while syncing usage metric", LogDb.DASHBOARD);
            }
        }
    }

    public static void calcAndSyncAccountUsage(int accountId){
        try {
            // Get organization to which account belongs to
            Organization organization = OrganizationsDao.instance.findOne(
                    Filters.in(Organization.ACCOUNTS, accountId)
            );

            if (organization == null) {
                loggerMaker.errorAndAddToDb("Organization not found for account: " + accountId, LogDb.DASHBOARD);
                return;
            }

            loggerMaker.infoAndAddToDb(String.format("Measuring usage for %s / %d ", organization.getName(), accountId), LogDb.DASHBOARD);

            String organizationId = organization.getId();

            for (MetricTypes metricType : MetricTypes.values()) {

                UsageMetricInfo usageMetricInfo = UsageMetricInfoDao.instance.findOne(
                        UsageMetricsDao.generateFilter(organizationId, accountId, metricType)
                );

                if (usageMetricInfo == null) {
                    usageMetricInfo = new UsageMetricInfo(organizationId, accountId, metricType);
                    UsageMetricInfoDao.instance.insertOne(usageMetricInfo);
                }

                int syncEpoch = usageMetricInfo.getSyncEpoch();
                int measureEpoch = usageMetricInfo.getMeasureEpoch();

                // Reset measureEpoch every month
                if (Context.now() - measureEpoch > 2629746) {
                    if (syncEpoch > Context.now() - 86400) {
                        measureEpoch = Context.now();

                        UsageMetricInfoDao.instance.updateOne(
                                UsageMetricsDao.generateFilter(organizationId, accountId, metricType),
                                Updates.set(UsageMetricInfo.MEASURE_EPOCH, measureEpoch)
                        );
                    }

                }

                AccountSettings accountSettings = AccountSettingsDao.instance.findOne(
                        AccountSettingsDao.generateFilter()
                );
                String dashboardMode = DashboardMode.getDashboardMode().toString();
                String dashboardVersion = accountSettings.getDashboardVersion();

                UsageMetric usageMetric = new UsageMetric(
                        organizationId, accountId, metricType, syncEpoch, measureEpoch,
                        dashboardMode, dashboardVersion
                );

                //calculate usage for metric
                UsageMetricCalculator.calculateUsageMetric(usageMetric);

                UsageMetricsDao.instance.insertOne(usageMetric);
                loggerMaker.infoAndAddToDb("Usage metric inserted: " + usageMetric.getId(), LogDb.DASHBOARD);

                UsageMetricUtils.syncUsageMetricWithAkto(usageMetric);

                UsageMetricUtils.syncUsageMetricWithMixpanel(usageMetric);
                loggerMaker.infoAndAddToDb(String.format("Synced usage metric %s  %s/%d %s",
                                usageMetric.getId().toString(), usageMetric.getOrganizationId(), usageMetric.getAccountId(), usageMetric.getMetricType().toString()),
                        LogDb.DASHBOARD
                );
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Error while measuring usage for account %d. Error: %s", accountId, e.getMessage()), LogDb.DASHBOARD);
        }
    }
}
