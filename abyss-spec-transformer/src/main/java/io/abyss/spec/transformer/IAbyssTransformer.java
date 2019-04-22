package io.abyss.spec.transformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.json.JsonObject;
import javax.wsdl.WSDLException;


/**
 * @author hakdogan (hakdogan@kodcu.com)
 * Created on 2019-04-22
 */

public interface IAbyssTransformer {

    /**
     *
     * @param parh
     * @return
     * @throws JsonProcessingException
     * @throws WSDLException
     */
    JsonObject transform(String parh) throws JsonProcessingException, WSDLException;
}
