/*
 * Copyright 2019 Verapi Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.verapi.abyss.spec.transformer;

import com.fasterxml.jackson.core.JsonProcessingException;

import javax.wsdl.WSDLException;

/**
 * @author hakdogan (hakdogan@kodcu.com)
 * Created on 2019-04-23
 */

public interface IAbyssTransformer {

    /**
     * Transforms the WSDL which given with the path param
     *
     * @param path path url or directory
     * @return yaml file
     * @throws JsonProcessingException encountered problem while processing JSON content
     * @throws WSDLException           encountered problem while processing WSDL content
     */
    String transform(String path) throws JsonProcessingException, WSDLException;

    /**
     * Transforms the WSDL which given with the wsdl param
     *
     * @param documentBaseURI documentBaseURI URL of the definition of the WSDL it can be null or empty
     * @param wsdl wsdl content of the WSDL
     * @return yaml file
     * @throws JsonProcessingException encountered problem while processing JSON content
     * @throws WSDLException           encountered problem while processing WSDL content
     */
    String transform(String documentBaseURI, String wsdl) throws JsonProcessingException, WSDLException;

}
