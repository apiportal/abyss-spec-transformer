/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.verapi.abyss.spec.transformer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.ibm.wsdl.BindingOperationImpl;
import com.ibm.wsdl.extensions.http.HTTPAddressImpl;
import com.ibm.wsdl.extensions.http.HTTPBindingImpl;
import com.ibm.wsdl.extensions.http.HTTPOperationImpl;
import com.ibm.wsdl.extensions.schema.SchemaImpl;
import com.ibm.wsdl.extensions.schema.SchemaImportImpl;
import com.ibm.wsdl.extensions.schema.SchemaReferenceImpl;
import com.ibm.wsdl.extensions.soap.SOAPAddressImpl;
import com.ibm.wsdl.extensions.soap.SOAPOperationImpl;
import com.ibm.wsdl.extensions.soap12.SOAP12AddressImpl;
import com.ibm.wsdl.extensions.soap12.SOAP12OperationImpl;
import com.ibm.wsdl.xml.WSDLReaderImpl;
import com.sun.org.apache.xerces.internal.dom.DeferredTextImpl;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.oas.models.*;
import org.w3c.dom.*;

import javax.wsdl.*;
import javax.wsdl.extensions.ExtensibilityElement;
import java.io.StringReader;
import java.util.*;

import io.swagger.v3.oas.models.info.Info;
import org.xml.sax.InputSource;
import org.yaml.snakeyaml.Yaml;

/**
 * @author hakdogan (hakdogan@kodcu.com)
 * Created on 2019-04-23
 */

public class OpenAPITransformer implements IAbyssTransformer {

    private static final String SOAP11_SERVICE_DESCRIPTION = "Soap 1.1 Service";
    private static final String SOAP12_SERVICE_DESCRIPTION = "Soap 1.2 Service";
    private static final String HTTP_SERVICE_DESCRIPTION = "HTTP Service";
    private static final String DEFAULT_INFORMATION_VERSION = "1.0";
    private static final String DEFAULT_CONTENT_TYPE = "application/xml";
    private static final String DESCRIPTION = "description";
    private static final String STRING_TYPE = "string";
    private static final String INTEGER_TYPE = "integer";
    private static final String NUMBER_TYPE = "number";
    private static final String OBJECT_TYPE = "object";
    private static final String RESPONSE_KEY = "response";
    private static final String REQUEST_BODY_KEY = "requestBody";
    private static final String LOCATIONS_KEY = "locations";
    private static final String MESSAGE_EXTENSIONS = "x-messages";
    private static final String MESSAGE_EXTENSIONS_PREFIX = "#/components/x-messages/";
    private static final String DEFAULT_SUCCESS_RESPONSE_CODE = "200";
    private static final String DEFAULT_FAULT_RESPONSE_CODE = "500";
    private static final String DEFAULT_FAULT_RESPONSE_REF = "#/components/responses/500_GeneralFaultResponse";
    private static final String DEFAULT_GENERAL_FAULT_RESPONSE_KEY = String.join("_", DEFAULT_FAULT_RESPONSE_CODE, "GeneralFaultResponse");

    private Map<String, String> dataTypes;

    /**
     * Constructor.
     */
    public OpenAPITransformer() {

        dataTypes = new HashMap<>();
        dataTypes.put(STRING_TYPE, STRING_TYPE);
        dataTypes.put(INTEGER_TYPE, INTEGER_TYPE);
        dataTypes.put("int", INTEGER_TYPE);
        dataTypes.put("float", NUMBER_TYPE);
        dataTypes.put("long", NUMBER_TYPE);
        dataTypes.put("double", NUMBER_TYPE);
        dataTypes.put("decimal", NUMBER_TYPE);
        dataTypes.put("short", NUMBER_TYPE);
        dataTypes.put("unsignedShort", NUMBER_TYPE);
        dataTypes.put("date", STRING_TYPE);
        dataTypes.put("dateTime", STRING_TYPE);
        dataTypes.put("time", STRING_TYPE);
        dataTypes.put("enum", STRING_TYPE);
        dataTypes.put("array", "array");
        dataTypes.put("boolean", "boolean");
    }

    /**
     * @param path path url or directory
     * @return yaml file
     * @throws JsonProcessingException encountered problem while processing JSON content
     * throws WSDLException            encountered problem while processing WSDL content
     * @see com.verapi.abyss.spec.transformer.IAbyssTransformer#transform(String)
     * <p>
     * Transforms the WSDL which given with the path param
     */
    @Override
    public String transform(final String path) throws JsonProcessingException, WSDLException {
        final Definition definition = getDefinition(path);
        final OpenAPI openAPI = getOpenAPI(definition);
        return generateYamlFile(openAPI);
    }

    /**
     * @param documentBaseURI documentBaseURI URL of the definition of the WSDL it can be null or empty
     * @param wsdl wsdl content of the WSDL
     * @return
     * @throws JsonProcessingException encountered problem while processing WSDL content
     * @throws WSDLException           encountered problem while processing WSDL content
     * @see com.verapi.abyss.spec.transformer.IAbyssTransformer#transform(String, String)
     * <p>
     * Transforms the WSDL which given with the wsdl param
     */
    @Override
    public String transform(final String documentBaseURI, final String wsdl) throws JsonProcessingException, WSDLException {
        final Definition definition = getDefinition(documentBaseURI, wsdl);
        final OpenAPI openAPI = getOpenAPI(definition);
        return generateYamlFile(openAPI);
    }

    /**
     *
     * @param path path url or directory
     * @return definiton of the WSDL
     * @throws WSDLException encountered problem while processing WSDL content
     */
    protected Definition getDefinition(final String path) throws WSDLException {
       return new WSDLReaderImpl().readWSDL(path);
    }

    /**
     *
     * @param documentBaseURI documentBaseURI URL of the definition of the WSDL it can be null or empty
     * @param wsdl wsdl content of the WSDL
     * @return definiton of the WSDL
     * @throws WSDLException encountered problem while processing WSDL content
     */
    protected Definition getDefinition(final String documentBaseURI, final String wsdl) throws WSDLException {
        return new WSDLReaderImpl().readWSDL(documentBaseURI, new InputSource(new StringReader(wsdl)));
    }

    /**
     *
     * @param definition definiton of the WSDL
     * @return openAPI
     */
    protected OpenAPI getOpenAPI(final Definition definition){

        final Map<String, Map<String, Object>> portBindingsMap = new HashMap<>();
        final OpenAPI openAPI = new OpenAPI();

        resolveServers(definition.getAllServices().values(), portBindingsMap, openAPI)
                .resolveTagsAndSetPaths(definition.getAllBindings().values(), portBindingsMap, openAPI)
                .getSchemas(definition.getTypes(), openAPI)
                .resolveMessages(definition.getMessages().values(), openAPI)
                .addRequestBodiesAndResponses(definition.getAllPortTypes().values(), openAPI);

        return openAPI;
    }

    /**
     * Resolves servers from services collection
     *
     * @param services        services that are resolved from WSDL
     * @param portBindingsMap port bindings map include binding definitions, it forwarded to other methods
     * @param openAPI         opeanAPI
     * @return OpenAPITransformer
     */
    private OpenAPITransformer resolveServers(final Collection<Service> services, final Map<String, Map<String, Object>> portBindingsMap,
                                              final OpenAPI openAPI) {
        final List<Server> serverList = new ArrayList<>();
        services.forEach(service -> {
            setInformation(service.getQName().getLocalPart(), openAPI);
            Optional.ofNullable(service.getDocumentationElement()).ifPresent(element ->
                    openAPI.getInfo().setDescription(getDocumentation(element.getChildNodes())));
            resolvePortsAndBindings(service.getPorts().values(), portBindingsMap)
                    .forEach(location -> {
                        final Server server = new Server();
                        server.setUrl((String) location);
                        serverList.add(server);
                    });
        });
        openAPI.setServers(serverList);
        return this;
    }

    /**
     * Iterates ports for resolve binding name and accessing extensibility elements
     *
     * @param ports           ports that are resolved from WSDL
     * @param portBindingsMap port bindings map include binding definitions, it forwarded to other methods
     * @return locations list that identify servers
     */
    private final Set<String> resolvePortsAndBindings(final Collection<Port> ports, final Map<String, Map<String, Object>> portBindingsMap) {
        final Set<String> locations = new HashSet<>();
        for (Port port : ports) {
            final String bindingName = port.getBinding().getQName().getLocalPart();
            resolvePortExtensibilityElement(port, bindingName, port.getExtensibilityElements(), portBindingsMap, locations);
        }

        return locations;
    }

    /**
     * Iterates extensibility elements for resolve information of soap and http operations
     *
     * @param port            port it is resolved from WSDL
     * @param bindingName     binding name
     * @param extElements     extensibility elements of the port
     * @param portBindingsMap port bindings map include binding definitions
     * @param locations       locations list that identify servers
     */
    private void resolvePortExtensibilityElement(final Port port, final String bindingName, final List<ExtensibilityElement> extElements,
                                                 final Map<String, Map<String, Object>> portBindingsMap, final Set<String> locations) {
        extElements.forEach(element -> {
            final Map<String, Object> portBinginAttributes = new HashMap<>();
            if (element instanceof SOAPAddressImpl) {
                portBinginAttributes.put("url", ((SOAPAddressImpl) element).getLocationURI());
                portBinginAttributes.put(DESCRIPTION, SOAP11_SERVICE_DESCRIPTION);
                addOperationsName(port.getBinding().getBindingOperations(), portBinginAttributes);
                portBindingsMap.put(bindingName, portBinginAttributes);
                locations.add(((SOAPAddressImpl) element).getLocationURI());
            } else if (element instanceof SOAP12AddressImpl) {
                portBinginAttributes.put("url", ((SOAP12AddressImpl) element).getLocationURI());
                portBinginAttributes.put(DESCRIPTION, SOAP12_SERVICE_DESCRIPTION);
                addOperationsName(port.getBinding().getBindingOperations(), portBinginAttributes);
                portBindingsMap.put(bindingName, portBinginAttributes);
                locations.add(((SOAP12AddressImpl) element).getLocationURI());
            } else if (element instanceof HTTPAddressImpl) {
                portBinginAttributes.put("url", ((HTTPAddressImpl) element).getLocationURI());
                portBinginAttributes.put(DESCRIPTION, HTTP_SERVICE_DESCRIPTION);
                addOperationsName(port.getBinding().getBindingOperations(), portBinginAttributes);
                portBindingsMap.put(bindingName, portBinginAttributes);
                locations.add(((HTTPAddressImpl) element).getLocationURI());
            }
        });
    }

    /**
     * Resolves schemas from types which are declared in the WSDL
     *
     * @param types   types it is resolved from WSDL
     * @param openAPI opeanAPI
     * @return OpenAPITransformer
     */
    private OpenAPITransformer getSchemas(final Types types, final OpenAPI openAPI) {
        final Components components = new Components();
        final Map<String, Schema> resolvedSchemas = new HashMap<>();
        Optional.ofNullable(types).ifPresent(type -> {
            final List<SchemaImpl> exElements = type.getExtensibilityElements();
            exElements.forEach(element -> {
                resolveIncludedSchemas(element.getIncludes(), resolvedSchemas);
                resolveImportedSchemas(element.getImports(), resolvedSchemas);
                final Element dom = element.getElement();
                resolvedSchemas.putAll(saveSchemas(dom.getChildNodes()));
            });
        });
        openAPI.setComponents(components.schemas(resolvedSchemas));
        return this;
    }

    /**
     * Resolves schemas from types which are included in the WSDL
     *
     * @param schemaReferences schema references includes schemas included in WSDL
     * @param resolvedSchemas  it contains resolved schemas
     */
    private void resolveIncludedSchemas(List<SchemaReferenceImpl> schemaReferences, final Map<String, Schema> resolvedSchemas) {
        schemaReferences.forEach(schema -> resolvedSchemas.putAll(saveSchemas(schema.getReferencedSchema().getElement().getChildNodes())));

    }

    /**
     * Resolves schemas from types which are imported in the WSDL
     *
     * @param imports         imports includes schemas imported in WSDL
     * @param resolvedSchemas it contains resolved schemas
     */
    private void resolveImportedSchemas(final Map<String, Vector> imports, final Map<String, Schema> resolvedSchemas) {
        imports.forEach((k, v) ->
                v.forEach(vector -> {
                    SchemaImportImpl impl = (SchemaImportImpl) vector;
                    Optional.ofNullable(impl.getReferencedSchema()).ifPresent(schema -> resolvedSchemas.putAll(saveSchemas(schema.getElement().getChildNodes())));
                }));
    }

    /**
     * Generates schemas map for OpenAPI
     *
     * @param nodeList node list child nodes from dom
     * @return resolved schemas
     */
    private Map<String, Schema> saveSchemas(final NodeList nodeList) {
        final Map<String, Schema> schemas = new HashMap<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            final Node parentNode = nodeList.item(i);
            final Schema childComplexTypeSchema = checkComplexTypeInChildNodes(parentNode.getChildNodes());
            if (null != childComplexTypeSchema) {
                Optional.ofNullable(childComplexTypeSchema.getName()).ifPresent(name -> schemas.put(name, childComplexTypeSchema));
            } else if (null != parentNode.getAttributes()) {
                final Schema schema = parseAttributes(parentNode);
                Optional.ofNullable(schema.getName()).ifPresent(name -> schemas.put(name, schema));
            }
        }

        return schemas;
    }

    /**
     * Resolves complex type
     *
     * @param nodeList node list
     * @return schema
     */
    private Schema checkComplexTypeInChildNodes(final NodeList nodeList) {
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeName().contains("complexType")) {
                final Schema schema = new Schema();
                schema.setName(node.getParentNode().getAttributes().item(0).getNodeValue());
                schema.setType(OBJECT_TYPE);
                parseComplexType(schema, node);
                return schema;
            }
        }
        return null;
    }


    /**
     * Resolves given node
     *
     * @param parentNode parent node
     * @return schema
     */
    private Schema parseAttributes(final Node parentNode) {
        final Schema schema = new Schema();
        final NamedNodeMap namedNodeMap = parentNode.getAttributes();
        for (int i = 0; i < namedNodeMap.getLength(); i++) {
            final Node node = namedNodeMap.item(i);
            if (parentNode.getNodeName().contains("complexType")) {
                schema.setType(OBJECT_TYPE);
                parseComplexType(schema, parentNode);
            } else if (parentNode.getNodeName().contains("simpleType")) {
                parseSimpleTypeObject(schema, parentNode.getChildNodes());
            }
            setSchemaDefinition(schema, node);
        }
        return schema;
    }

    /**
     * Parsing complex type
     *
     * @param schema      schema
     * @param complexType complex type
     */
    private void parseComplexType(final Schema schema, final Node complexType) {
        NodeList complexTypeChildren = complexType.getChildNodes();
        for (int i = 0; i < complexTypeChildren.getLength(); i++) {
            Node childNode = complexTypeChildren.item(i);
            if (childNode.getNextSibling() != null) {
                Node sibling = childNode.getNextSibling();
                if (sibling.getChildNodes().getLength() > 0) {
                    resolveSiblingChildNodes(schema, sibling.getChildNodes());
                }
            }
        }
    }

    /**
     * Parsing simple type
     *
     * @param schema   schema
     * @param nodeList node list
     */
    private void parseSimpleTypeObject(final Schema schema, final NodeList nodeList) {
        for (int i = 0; i < nodeList.getLength(); i++) {
            final Node node = nodeList.item(i);
            if (node.getNodeName().contains("restriction")) {
                final NamedNodeMap restrictionNodeMap = node.getAttributes();
                for (int j = 0; j < restrictionNodeMap.getLength(); j++) {
                    final Node restrictionNode = restrictionNodeMap.item(j);
                    if (restrictionNode.getNodeName().equals("base")) {
                        final String type = restrictionNode.getNodeValue();
                        schema.setType(type.substring(type.indexOf(':') + 1));
                    }
                }
            }
        }
    }

    /**
     * Resolves sibling childs
     *
     * @param siblingChilds sibling childs
     */
    private void resolveSiblingChildNodes(final Schema schema, final NodeList siblingChilds) {
        for (int i = 0; i < siblingChilds.getLength(); i++) {
            Node sequenceNode = siblingChilds.item(i);
            if (sequenceNode.getAttributes() != null && sequenceNode.getAttributes().getLength() > 0) {
                resolveSequenceNodeMap(schema, sequenceNode.getAttributes());
            }
        }
    }

    /**
     * Resolves sequence node map
     *
     * @param schema          schema
     * @param sequenceNodeMap sequence node map
     */
    private void resolveSequenceNodeMap(final Schema schema, final NamedNodeMap sequenceNodeMap) {
        for (int i = 0; i < sequenceNodeMap.getLength(); i++) {
            Node element = sequenceNodeMap.item(i);
            if (element.getNodeName().equals("type")) {
                setRequiredItemsAndPropertiesOnSchema(schema, sequenceNodeMap, element);
            }
        }
    }

    /**
     * Sets the required elements and properties in the schema
     *
     * @param schema       schema
     * @param namedNodeMap named node map
     * @param node         node
     */
    private void setRequiredItemsAndPropertiesOnSchema(final Schema schema, final NamedNodeMap namedNodeMap, final Node node) {
        for (int j = 0; j < namedNodeMap.getLength(); j++) {
            if (namedNodeMap.item(j).getNodeName().equals("name")) {
                schema.addRequiredItem(namedNodeMap.item(j).getNodeValue());
                final String type = node.getNodeValue().substring(node.getNodeValue().indexOf(':') + 1);
                if (null == dataTypes.get(type)) {
                    schema.addProperties(namedNodeMap.item(j).getNodeValue(), getReferenceItem(type));
                } else {
                    schema.addProperties(namedNodeMap.item(j).getNodeValue(), getPropertyItem(dataTypes.get(type)));
                }
            }
        }
    }

    /**
     * Sets the schema definition
     *
     * @param schema schema
     * @param node   node
     */
    private void setSchemaDefinition(final Schema schema, final Node node) {
        if (node.getNodeName().equals("name")) {
            schema.setName(node.getNodeValue());
            schema.setTitle(node.getNodeValue());
        } else if (node.getNodeName().equals("type")) {
            final String type = node.getNodeValue().substring(node.getNodeValue().indexOf(':') + 1);
            if (null != dataTypes.get(type)) {
                schema.setType(dataTypes.get(type));
            } else {
                schema.setType(OBJECT_TYPE);
                schema.addRequiredItem(type);
                schema.addProperties(type, getReferenceItem(type));
            }
        }
    }

    /**
     * Returns reference item for the schema
     *
     * @param type type
     * @return schema
     */
    private Schema getReferenceItem(final String type) {
        final Schema referenceItem = new Schema();
        referenceItem.set$ref(type);
        return referenceItem;
    }

    /**
     * Returns property item for the schema
     *
     * @param type type
     * @return schema
     */
    private Schema getPropertyItem(final String type) {
        final Schema propertyItem = new Schema();
        propertyItem.setType(type);
        return propertyItem;
    }

    /**
     * Resolves messages which are declared in the WSDL
     *
     * @param messages messages that are resolved from WSDL
     * @param openAPI  openAPI
     * @return OpenAPITransformer
     */
    private OpenAPITransformer resolveMessages(final Collection<Message> messages, final OpenAPI openAPI) {
        final Map<String, Object> extensions = new HashMap<>();
        final Map<String, Schema> schemaMap = openAPI.getComponents().getSchemas();

        messages.forEach(message -> {
            final String messageName = message.getQName().getLocalPart();
            final Map<String, String> attributes = new HashMap<>();
            attributes.put("$ref", "#/components/schemas/" + messageName);
            extensions.put(messageName, attributes);

            if (null == schemaMap.get(messageName)) {
                final Schema schema = new Schema();
                schema.setName(messageName);
                schema.setTitle(messageName);

                final Map<String, Part> parts = message.getParts();
                parts.forEach((k, v) -> {
                    Optional.ofNullable(v.getElementName()).ifPresent(element ->
                            getSchemaForMessages(schema, element.getLocalPart(), element.getLocalPart(), schemaMap));

                    Optional.ofNullable(v.getTypeName()).ifPresent(type ->
                            getSchemaForMessages(schema, v.getName(), type.getLocalPart(), schemaMap));
                });
                schemaMap.put(messageName, schema);
            }

        });
        openAPI.getComponents().addExtension(MESSAGE_EXTENSIONS, extensions);
        return this;
    }

    /**
     * Generates schemas for message extensions
     *
     * @param schema    schema
     * @param itemName  item name
     * @param type      type
     * @param schemaMap schema map
     */
    private void getSchemaForMessages(final Schema schema, final String itemName, final String type, final Map<String, Schema> schemaMap) {

        schema.addRequiredItem(itemName);
        Schema propertyItem = new Schema();
        if (null != schemaMap.get(type)) {
            propertyItem.set$ref("#/components/schemas/" + type);
        } else {
            propertyItem = getPropertyItem(dataTypes.get(type));
        }
        schema.addProperties(itemName, propertyItem);
    }

    /**
     * Resolves tags and paths from bindings for Open API
     *
     * @param bindings        bindings that are resolved from WSDL
     * @param portBindingsMap port bindings map include binding definitions
     * @param openAPI         openAPI
     */
    private OpenAPITransformer resolveTagsAndSetPaths(final Collection<Binding> bindings, final Map<String, Map<String, Object>> portBindingsMap, final OpenAPI openAPI) {
        final List<Tag> tagList = new ArrayList<>();
        final Map<String, Map<String, Object>> discoveredPathsMap = new HashMap<>();
        bindings.forEach(binding -> {
            final String httpMethod = resolveHttpMethod(binding.getExtensibilityElements());
            final Map<String, Object> tagAttributes = portBindingsMap.get(binding.getQName().getLocalPart());
            Optional.ofNullable(tagAttributes).ifPresent(attributes -> {
                final Tag tag = new Tag();
                final ExternalDocumentation externalDocs = new ExternalDocumentation();
                tag.setName(binding.getQName().getLocalPart());
                externalDocs.setUrl(attributes.get("url").toString());
                tag.setExternalDocs(externalDocs);
                externalDocs.setDescription((String) attributes.get(DESCRIPTION));
                tagList.add(tag);
            });
            discoverPaths(httpMethod, binding.getBindingOperations(), portBindingsMap, discoveredPathsMap);
        });

        openAPI.setTags(tagList);
        setPaths(discoveredPathsMap, openAPI);
        return this;
    }

    /**
     * Resolves http method HTTPBindingImpl
     *
     * @param exElements exElements list of extensibility element
     * @return http method
     */
    private String resolveHttpMethod(final List<ExtensibilityElement> exElements) {
        String httpMethod = "post";
        for (ExtensibilityElement element : exElements) {
            if (element instanceof HTTPBindingImpl) {
                httpMethod = ((HTTPBindingImpl) element).getVerb().toLowerCase();
            }
        }
        return httpMethod;
    }

    /**
     * Discovers paths from binding operations for Open API
     *
     * @param httpMethod        http method
     * @param bindingOperations binding operations that are resolved from the WSDL
     * @param portBindingsMap   port bindings map include binding definitions
     * @param discoveredPaths   discovered paths include paths
     */
    private void discoverPaths(final String httpMethod, final List<BindingOperationImpl> bindingOperations,
                               final Map<String, Map<String, Object>> portBindingsMap, final Map<String, Map<String, Object>> discoveredPaths) {
        for (BindingOperationImpl impl : bindingOperations) {
            final List<ExtensibilityElement> exList = impl.getExtensibilityElements();
            Map<String, Object> pathAttributes;
            for (ExtensibilityElement element : exList) {
                if (element instanceof SOAPOperationImpl || element instanceof SOAP12OperationImpl) {
                    pathAttributes = setPathAttributes("SoapService", impl.getName(), httpMethod,
                            impl.getOperation().getInput().getMessage(), impl.getOperation().getOutput().getMessage(),
                            impl.getOperation().getFaults());

                    addPathTags(impl.getName(), "soap", pathAttributes, portBindingsMap);
                    discoveredPaths.put(impl.getName(), pathAttributes);
                } else if (element instanceof HTTPOperationImpl) {
                    pathAttributes = setPathAttributes("HttpService", impl.getName(), httpMethod,
                            impl.getOperation().getInput().getMessage(), impl.getOperation().getOutput().getMessage(),
                            impl.getOperation().getFaults());
                    addPathTags(impl.getName(), "http", pathAttributes, portBindingsMap);
                    discoveredPaths.put(((HTTPOperationImpl) element).getLocationURI(), pathAttributes);
                }
            }
        }
    }

    /**
     * Defines path attributes for Open API
     *
     * @param description   path description
     * @param operationName operation name
     * @param httpMethod    http method
     * @param inputMessage  input message
     * @param outputMessage output message
     * @param faultMap      fault map
     * @return path attributes
     */
    private final Map<String, Object> setPathAttributes(final String description, final String operationName, final String httpMethod,
                                                        final Message inputMessage, final Message outputMessage, final Map<String, Fault> faultMap) {
        final Map<String, Object> pathAttributes = new HashMap<>();
        pathAttributes.put(DESCRIPTION, description);
        pathAttributes.put("operation", operationName);
        pathAttributes.put("method", httpMethod);

        Optional.ofNullable(inputMessage).ifPresent(message -> pathAttributes.put(REQUEST_BODY_KEY, message.getQName().getLocalPart()));
        Optional.ofNullable(outputMessage).ifPresent(message -> pathAttributes.put(RESPONSE_KEY, message.getQName().getLocalPart()));
        pathAttributes.put("faults", !faultMap.isEmpty());

        return pathAttributes;
    }

    /**
     * Defines tags for paths
     *
     * @param operationName   operation name
     * @param requestType     request type
     * @param pathAttributes  path attributes
     * @param portBindingsMap port bindings map include binding definitions
     */
    private void addPathTags(final String operationName, final String requestType,
                             final Map<String, Object> pathAttributes, final Map<String, Map<String, Object>> portBindingsMap) {
        portBindingsMap.forEach((key, value) -> {
            final Map<String, Object> attributes = value;
            attributes.forEach((k, v) -> {
                if (k.equals("operations")) {
                    final List<String> names = (List<String>) v;
                    final List<String> tags = null != pathAttributes.get("tags")
                            ? (List<String>) pathAttributes.get("tags") : new ArrayList<>();
                    final Set<String> locations = null != pathAttributes.get(LOCATIONS_KEY)
                            ? (Set<String>) pathAttributes.get(LOCATIONS_KEY) : new HashSet<>();
                    names.forEach(n -> {
                        final String desc = value.get(DESCRIPTION).toString().toLowerCase();
                        if (n.equals(operationName) && desc.contains(requestType)) {
                            tags.add(key);
                            locations.add((String) value.get("url"));
                        }
                        pathAttributes.put("tags", tags);
                        pathAttributes.put(LOCATIONS_KEY, locations);
                    });
                }
            });
        });
    }

    /**
     * Resolves documadocumentationntation information from WSDL
     *
     * @param nodeList node list
     * @return documentation information
     */
    private String getDocumentation(final NodeList nodeList) {
        String data = "";
        for (int i = 0; i < nodeList.getLength(); i++) {
            final Node node = nodeList.item(i);
            data = Optional.ofNullable(((DeferredTextImpl) node).getData()).orElse("");
        }

        return data;
    }


    /**
     * Sets documentation information on Open API
     *
     * @param title   title
     * @param openAPI openAPI
     */
    private void setInformation(final String title, final OpenAPI openAPI) {
        Info info = new Info();
        info.setTitle(title);
        info.setVersion(DEFAULT_INFORMATION_VERSION);
        openAPI.setInfo(info);
    }

    /**
     * Adds operation name on por port binding attributes map
     *
     * @param bindingOperations     binding operations
     * @param portBindingAttributes port binding attributes
     */
    private void addOperationsName(final List<BindingOperation> bindingOperations, final Map<String, Object> portBindingAttributes) {
        final List<String> names = new ArrayList<>();
        bindingOperations.forEach(operation -> names.add(operation.getName()));
        portBindingAttributes.put("operations", names);
    }

    /**
     * Sets paths on Open API
     *
     * @param discoveredPathsMap discovered paths map
     * @param openAPI            openAPI
     */
    private void setPaths(final Map<String, Map<String, Object>> discoveredPathsMap, final OpenAPI openAPI) {

        final Paths paths = new Paths();
        discoveredPathsMap.forEach((k, v) -> {
            final Operation operation = new Operation();
            final String operationId = (String) v.get("operation");
            Optional.ofNullable(v.get(REQUEST_BODY_KEY)).ifPresent(key -> operation.requestBody(getRequestBodyForPath((String) key)));
            Optional.ofNullable(v.get(RESPONSE_KEY)).ifPresent(key -> operation.setResponses(getApiResponseForPath((String) key, (boolean) v.get("faults"))));
            Optional.ofNullable(v.get("tags")).ifPresent(tags -> operation.setTags((List<String>) tags));
            operation.setOperationId(String.join("_", operationId, v.get(DESCRIPTION).toString()));
            final PathItem pathItem = getPathItem((String) v.get("method"), operation);
            overrideServersIfMoreThanOne(openAPI.getServers(), (Set<String>) v.get(LOCATIONS_KEY), pathItem);
            paths.addPathItem("/" + k, pathItem);

        });

        openAPI.setPaths(paths);
    }

    /**
     * Returns request body for Open API
     *
     * @param bodyName body name
     * @return RequestBody
     */
    private RequestBody getRequestBodyForPath(final String bodyName) {
        final RequestBody requestBody = new RequestBody();
        requestBody.set$ref("#/components/requestBodies/" + bodyName);
        return requestBody;
    }

    /**
     * Returns api responses for Open API
     *
     * @param response response
     * @param faults   faults
     * @return ApiResponses
     */
    private ApiResponses getApiResponseForPath(final String response, final boolean faults) {
        final ApiResponses apiResponses = new ApiResponses();
        final ApiResponse okResponse = new ApiResponse();
        okResponse.set$ref("#/components/responses/200_" + response);
        apiResponses.addApiResponse(DEFAULT_SUCCESS_RESPONSE_CODE, okResponse);

        if (faults) {
            final ApiResponse faultResponse = new ApiResponse();
            faultResponse.set$ref(DEFAULT_FAULT_RESPONSE_REF);
            apiResponses.addApiResponse(DEFAULT_FAULT_RESPONSE_CODE, faultResponse);
        }

        return apiResponses;
    }

    /**
     * Returns path item for Open API
     *
     * @param httpMethod http method
     * @return PathItem
     */
    private PathItem getPathItem(final String httpMethod, final Operation operation) {
        final PathItem pathItem = new PathItem();
        switch (httpMethod) {
            case "get":
                pathItem.setGet(operation);
                break;
            case "put":
                pathItem.setPut(operation);
                break;
            case "delete":
                pathItem.setDelete(operation);
                break;
            case "options":
                pathItem.setOptions(operation);
                break;
            default:
                pathItem.setPost(operation);
                break;
        }

        return pathItem;
    }

    /**
     * Overrides servers on paths if more than one
     *
     * @param servers  servers
     * @param pathItem path item
     */
    private void overrideServersIfMoreThanOne(final List<Server> servers, final Set<String> location, final PathItem pathItem) {
        if (servers.size() > 1) {
            final List<Server> serverList = new ArrayList<>();
            location.forEach(s -> {
                final Server server = new Server();
                server.setUrl(s);
                serverList.add(server);
            });

            pathItem.servers(serverList);
        }
    }

    /**
     * Adds request bodies and responses on Open API
     *
     * @param portTypes port types
     * @param openAPI   openAPI
     * @return OpenAPITransformer
     */
    private OpenAPITransformer addRequestBodiesAndResponses(final Collection<PortType> portTypes, final OpenAPI openAPI) {
        final Optional<Map<String, Object>> componentExt = Optional.ofNullable(openAPI.getComponents().getExtensions());
        componentExt.ifPresent(ext -> {
            final Map<String, Object> extensions = (Map<String, Object>) ext.get(MESSAGE_EXTENSIONS);
            for (PortType portType : portTypes) {
                final List<javax.wsdl.Operation> operations = portType.getOperations();
                for (javax.wsdl.Operation operation : operations) {
                    if (null != operation.getInput().getMessage()) {
                        addRequestBodies(operation.getInput().getMessage().getQName().getLocalPart(), extensions, openAPI);
                    }
                    if (null != operation.getOutput().getMessage()) {
                        addResponses(operation.getOutput().getMessage().getQName().getLocalPart(), extensions, openAPI);
                    }
                    if (!operation.getFaults().isEmpty()) {
                        addFaultResponses(operation.getFaults(), openAPI);
                    }
                }
            }
        });

        return this;
    }

    /**
     * Adds request bodies on Open API
     *
     * @param bodyName   body name
     * @param extensions extensions
     * @param openAPI    openAPI
     */
    private void addRequestBodies(final String bodyName, final Map<String, Object> extensions, final OpenAPI openAPI) {
        if (null != extensions.get(bodyName)) {
            final RequestBody requestBody = new RequestBody();
            final Schema bodySchema = new Schema();
            requestBody.setDescription(bodyName + " message object as request body");
            requestBody.setContent(new Content().addMediaType(DEFAULT_CONTENT_TYPE, new MediaType().schema(bodySchema)));
            bodySchema.set$ref(MESSAGE_EXTENSIONS_PREFIX + bodyName);
            requestBody.setRequired(true);
            openAPI.getComponents().addRequestBodies(bodyName, requestBody);
        }
    }

    /**
     * Adds responses on Open API
     *
     * @param responseName response name
     * @param extensions   extensions
     * @param openAPI      openAPI
     */
    private void addResponses(final String responseName, final Map<String, Object> extensions, final OpenAPI openAPI) {
        if (null != extensions.get(responseName)) {
            openAPI.getComponents().addResponses(String.join("_", DEFAULT_SUCCESS_RESPONSE_CODE, responseName), getResponse(responseName, "Successful operation"));
        }
    }

    /**
     * Returns api response
     *
     * @param name        name
     * @param description description
     * @return ApiResponse
     */
    private ApiResponse getResponse(final String name, final String description) {
        final ApiResponse apiResponse = new ApiResponse();
        final Schema responseSchema = new Schema();
        responseSchema.set$ref(MESSAGE_EXTENSIONS_PREFIX + name);
        apiResponse.setContent(new Content().addMediaType(DEFAULT_CONTENT_TYPE, new MediaType().schema(responseSchema)));
        apiResponse.setDescription(description);

        return apiResponse;
    }

    /**
     * Adds fault responses on Open API
     *
     * @param faultMap fault map
     * @param openAPI  openAPI
     */
    private void addFaultResponses(final Map<String, Fault> faultMap, final OpenAPI openAPI) {
        final ComposedSchema composedSchema = new ComposedSchema();
        faultMap.forEach((k, v) ->
                Optional.ofNullable(v.getMessage().getQName()).ifPresent(q -> {
                    final Schema schema = new Schema();
                    schema.set$ref("#/components/" + MESSAGE_EXTENSIONS + "/" + q.getLocalPart());
                    composedSchema.addOneOfItem(schema);
                }));

        final ApiResponse faultResponse = new ApiResponse();
        faultResponse.setDescription("Fault Error");
        faultResponse.setContent(new Content().addMediaType(DEFAULT_CONTENT_TYPE, new MediaType().schema(composedSchema)));
        openAPI.getComponents().addResponses(DEFAULT_GENERAL_FAULT_RESPONSE_KEY, faultResponse);
    }

    /**
     * Returns yaml file
     *
     * @param openAPI openAPI
     * @return yaml file
     * @throws JsonProcessingException encountered problem while processing JSON content
     */
    private String generateYamlFile(final OpenAPI openAPI) throws JsonProcessingException {
        final YAMLFactory yamlFactory = new YAMLFactory()
                .configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true)
                .configure(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS, true);

        final YAMLMapper yamlMapper = new YAMLMapper(yamlFactory);
        yamlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        final Yaml yaml = new Yaml();

        Map<String, Object> yamlFile = yaml.load(yamlMapper.writeValueAsString(openAPI));
        Map<String, Object> components = (Map<String, Object>) yamlFile.get("components");
        Map<String, Object> extensions = (Map<String, Object>) components.get("extensions");
        Map<String, Object> messages = (Map<String, Object>) extensions.get(MESSAGE_EXTENSIONS);

        components.remove("extensions");
        components.put(MESSAGE_EXTENSIONS, messages);

        return yamlMapper.writeValueAsString(yamlFile);
    }
}
