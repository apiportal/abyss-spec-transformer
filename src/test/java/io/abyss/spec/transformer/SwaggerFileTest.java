package io.abyss.spec.transformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static org.junit.Assert.assertFalse;
import javax.wsdl.WSDLException;

/**
 * @author hakdogan (hakdogan@kodcu.com)
 * Created on 2019-04-23
 */

@RunWith(JUnit4.class)
public class SwaggerFileTest {

    private static final String WSDL_URL = "src/main/resources/wsdl/thomas-bayer.wsdl";

    @Test
    public void aTest() throws WSDLException, JsonProcessingException {
        final String yamlFile = new OpenAPITransformer().transform(WSDL_URL);
        assertFalse(yamlFile.isEmpty());
    }
}
