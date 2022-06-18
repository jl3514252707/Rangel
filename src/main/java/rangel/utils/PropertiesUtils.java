package rangel.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 配置文件工具类
 *
 * @author 软工20-2金磊
 */
public class PropertiesUtils {
    private static String getProperties(String fileName, String propertyName) {
        try {
            InputStream inputStream = LogHelper.class.getResourceAsStream("/"+fileName);
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties.getProperty(propertyName);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 从配置文件中获取boolean类型的属性,如果没有加载配置文件,返回默认值
     * @param fileName 配置文件名
     * @param propertyName 属性名
     * @param defaultValue 默认值
     * @return java.lang.Boolean 属性
     * @author 软工20-2金磊
     * @since 2022/6/18
     */
    public static Boolean getBoolean(String fileName, String propertyName, Boolean defaultValue){
        String property=getProperties(fileName,propertyName);
        if ("".equals(property)){
            return defaultValue;
        }else {
            return Boolean.parseBoolean(property);
        }
    }
}
