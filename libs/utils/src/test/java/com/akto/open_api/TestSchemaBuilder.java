package com.akto.open_api;

import com.akto.dto.type.SingleTypeInfo;
import io.swagger.v3.oas.models.media.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.akto.open_api.TestCustomSchemasFromSingleTypeInfo.generateSingleTypeInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class TestSchemaBuilder {

    @Test
    public void testSimpleObject() throws Exception {
        SingleTypeInfo s1 = generateSingleTypeInfo("user#name#first", SingleTypeInfo.SubType.GENERIC);
        SingleTypeInfo s2 = generateSingleTypeInfo("user#name#last", SingleTypeInfo.SubType.GENERIC);
        SingleTypeInfo s3 = generateSingleTypeInfo("user#age", SingleTypeInfo.SubType.INTEGER_32);
        SingleTypeInfo s4 = generateSingleTypeInfo("id", SingleTypeInfo.SubType.INTEGER_64);
        SingleTypeInfo s5 = generateSingleTypeInfo("email", SingleTypeInfo.SubType.EMAIL);
        SingleTypeInfo s6 = generateSingleTypeInfo("cards#$#id", SingleTypeInfo.SubType.GENERIC);
        SingleTypeInfo s7 = generateSingleTypeInfo("cards#$#name", SingleTypeInfo.SubType.GENERIC);
        SingleTypeInfo s8 = generateSingleTypeInfo("cards#$#dashboard#$#dashboard_id", SingleTypeInfo.SubType.UUID);
        SingleTypeInfo s9 = generateSingleTypeInfo("cards#$#dashboard#$#name", SingleTypeInfo.SubType.GENERIC);
        SingleTypeInfo s10 = generateSingleTypeInfo("cards#$#dashboard#$#time", SingleTypeInfo.SubType.INTEGER_64);
        SingleTypeInfo s11 = generateSingleTypeInfo("prices#$", SingleTypeInfo.SubType.FLOAT);
        List<SingleTypeInfo> singleTypeInfoList = Arrays.asList(s1,s2,s3,s4,s5,s6,s7,s8,s9,s10,s11);

        Schema<?> schema =  Main.buildSchema(singleTypeInfoList);
        assertEquals(schema.getClass(), ObjectSchema.class);

        //noinspection rawtypes
        Map<String,Schema> propertiesMap = schema.getProperties();

        assertEquals(propertiesMap.get("user").getClass(),ObjectSchema.class);
        assertEquals(propertiesMap.get("id").getClass(), IntegerSchema.class);
        assertEquals(propertiesMap.get("email").getClass(), EmailSchema.class);
        assertEquals(propertiesMap.get("cards").getClass(), ArraySchema.class);
        assertEquals(propertiesMap.get("prices").getClass(), ArraySchema.class);

        ObjectSchema userSchema = (ObjectSchema) propertiesMap.get("user");
        //noinspection rawtypes
        Map<String,Schema> userPropertiesMap = userSchema.getProperties();
        assertEquals(userPropertiesMap.get("name").getClass(), ObjectSchema.class);
        assertEquals(userPropertiesMap.get("age").getClass(), IntegerSchema.class);

        ObjectSchema userNameSchema = (ObjectSchema) userPropertiesMap.get("name");
        //noinspection rawtypes
        Map<String,Schema> userNamePropertiesMap = userNameSchema.getProperties();
        assertEquals(userNamePropertiesMap.get("first").getClass(), StringSchema.class);
        assertEquals(userNamePropertiesMap.get("last").getClass(), StringSchema.class);

        ArraySchema cardsSchema = (ArraySchema) propertiesMap.get("cards");
        Schema<?> cardItems = cardsSchema.getItems();
        assertEquals(cardItems.getClass(), ObjectSchema.class);

        //noinspection rawtypes
        Map<String,Schema> cardItemPropertiesMap = cardItems.getProperties();
        assertEquals(cardItemPropertiesMap.get("dashboard").getClass(), ArraySchema.class);

        ArraySchema dashboardSchema = (ArraySchema) cardItemPropertiesMap.get("dashboard");
        Schema<?> dashboardItems = dashboardSchema.getItems();
        assertEquals(dashboardItems.getClass(), ObjectSchema.class);

        //noinspection rawtypes
        Map<String,Schema> dashboardItemsPropertiesMap = dashboardItems.getProperties();
        assertEquals(dashboardItemsPropertiesMap.get("name").getClass(), StringSchema.class);
        assertEquals(dashboardItemsPropertiesMap.get("time").getClass(), IntegerSchema.class);

        Schema<?> pricesItems = ((ArraySchema) propertiesMap.get("prices")).getItems();
        assertEquals(pricesItems.getClass(), NumberSchema.class);
    }
}
