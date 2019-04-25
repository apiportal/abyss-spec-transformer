package com.verapi.abyss.spec.transformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

import javax.wsdl.Definition;
import javax.wsdl.WSDLException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

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
    public void wsdlTransformWithStringContent() throws IOException, WSDLException {
        final String content = new String(Files.readAllBytes(Paths.get(WSDL_LOCATION)));
        final String yamlFile = transformer.transform(DOCUMENT_BASE_URI, content);
        assertFalse(yamlFile.isEmpty());
    }

    @Test
    public void mismatcTesthWithURLOrDirectoryPath() throws WSDLException {
        final Definition definition = transformer.getDefinition(WSDL_LOCATION);
        final OpenAPI openAPI = transformer.getOpenAPI(definition);
        Map<String, Object> extensions = (Map<String, Object>) openAPI.getComponents().getExtensions().get("x-messages");
        assertEquals(definition.getMessages().size(), extensions.size());
        assertEquals(definition.getServices().size(), openAPI.getServers().size());
    }

    @Test
    public void mismatchTestWithStringContent() throws WSDLException, IOException {
        String content = new String(Files.readAllBytes(Paths.get(WSDL_LOCATION)));
        final Definition definition = transformer.getDefinition(null, content);
        final OpenAPI openAPI = transformer.getOpenAPI(definition);
        Map<String, Object> extensions = (Map<String, Object>) openAPI.getComponents().getExtensions().get("x-messages");
        assertEquals(definition.getMessages().size(), extensions.size());
        assertEquals(definition.getServices().size(), openAPI.getServers().size());
    }
}
