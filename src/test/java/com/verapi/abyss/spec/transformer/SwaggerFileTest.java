package com.verapi.abyss.spec.transformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertFalse;

import javax.wsdl.WSDLException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author hakdogan (hakdogan@kodcu.com)
 * Created on 2019-04-23
 */

@RunWith(JUnit4.class)
public class SwaggerFileTest {

    private static final String WSDL_LOCATION = "src/main/resources/wsdl/thomas-bayer.wsdl";
    private static final String DOCUMENT_BASE_URI = "http://www.thomas-bayer.com/axis2/services/BLZService?wsdl";
    private final OpenAPITransformer transformer = new OpenAPITransformer();

    @Test
    public void wsdlTransformWithURLOrDirectoryPath() throws WSDLException, JsonProcessingException {
        final String yamlFile = transformer.transform(WSDL_LOCATION);
        assertFalse(yamlFile.isEmpty());
    }

    @Test
    public void wsdlTransformWithString() throws IOException, WSDLException {
        String contents = new String(Files.readAllBytes(Paths.get(WSDL_LOCATION)));
        final String yamlFile = transformer.transform(DOCUMENT_BASE_URI, contents);
        assertFalse(yamlFile.isEmpty());
    }
}
