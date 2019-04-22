package io.abyss.spec.transformer;

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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.*;
import javax.wsdl.*;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.xml.WSDLReader;
import java.util.*;
import io.swagger.v3.oas.models.info.Info;
import org.yaml.snakeyaml.Yaml;

/**
 * @author hakdogan (hakdogan@kodcu.com)
 * Created on 2019-04-22
 */

@Slf4j
public class OpenAPITransformer implements IAbyssTransformer
{

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
     *
     * @param path
     * @return
     * @throws JsonProcessingException
     * @throws WSDLException
     */
    @Override
    public JsonObject transform(final String path) throws JsonProcessingException, WSDLException {

        final WSDLReader wsdlReader = new WSDLReaderImpl();
        final Definition definition = wsdlReader.readWSDL(path);
        final Map<String, Map<String, Object>> portBindingNames = new HashMap<>();
        final OpenAPI openAPI = new OpenAPI();

        resolveServers(definition.getAllServices().values(), portBindingNames, openAPI)
                .resolveTagsAndSetPaths(definition.getAllBindings().values(), portBindingNames, openAPI)
                .getSchemas(definition.getTypes(), openAPI)
                .resolveMessages(definition.getMessages().values(), openAPI)
                .addRequestBodiesAndResponses(definition.getAllPortTypes().values(), openAPI);

        final String yamlFile = generateYamlFile(openAPI);
        return new JsonObject().put("warnings", checksum(definition, openAPI)).put("yamlFile", yamlFile);
    }

    /**
     *
     * @param services
     * @param portBinginNames
     * @param openAPI
     * @return
     */
    private OpenAPITransformer resolveServers(final Collection<Service> services, final Map<String, Map<String, Object>> portBinginNames,
                                              final OpenAPI openAPI){
        final List<Server> serverList = new ArrayList<>();
        services.forEach(service -> {
            setInformation(service.getQName().getLocalPart(), openAPI);
            Optional.ofNullable(service.getDocumentationElement()).ifPresent(element ->
                    openAPI.getInfo().setDescription(getDocumantation(element.getChildNodes())));
            resolvePortsAndBindings(service.getPorts().values(), portBinginNames)
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
     *
     * @param ports
     * @param portBindingNames
     * @return
     */
    private final Set<String> resolvePortsAndBindings(final Collection<Port> ports, final Map<String, Map<String, Object>> portBindingNames){
        final Set<String> locations = new HashSet<>();
        for(Port port :ports){
            final String portBindingsName = port.getBinding().getQName().getLocalPart();
            resolvePortExtensibilityElement(port, portBindingsName, port.getExtensibilityElements(), portBindingNames, locations);
        }

        return locations;
    }

    /**
     *
     * @param port
     * @param portBindingsName
     * @param extElements
     * @param portBinginNames
     * @param locations
     */
    private void resolvePortExtensibilityElement(final Port port, final String portBindingsName, final List<ExtensibilityElement> extElements,
                                                 final Map<String, Map<String, Object>> portBinginNames, final Set<String> locations){
        extElements.forEach(element -> {
            final Map<String, Object> portBinginAttributes = new HashMap<>();
            if(element instanceof SOAPAddressImpl){
                portBinginAttributes.put("url", ((SOAPAddressImpl) element).getLocationURI());
                portBinginAttributes.put(DESCRIPTION, SOAP11_SERVICE_DESCRIPTION);
                addOperationsName(port.getBinding().getBindingOperations(), portBinginAttributes);
                portBinginNames.put(portBindingsName, portBinginAttributes);
                locations.add(((SOAPAddressImpl) element).getLocationURI());
            } else if(element instanceof SOAP12AddressImpl){
                portBinginAttributes.put("url", ((SOAP12AddressImpl) element).getLocationURI());
                portBinginAttributes.put(DESCRIPTION, SOAP12_SERVICE_DESCRIPTION);
                addOperationsName(port.getBinding().getBindingOperations(), portBinginAttributes);
                portBinginNames.put(portBindingsName, portBinginAttributes);
                locations.add(((SOAP12AddressImpl) element).getLocationURI());
            } else if(element instanceof HTTPAddressImpl){
                portBinginAttributes.put("url", ((HTTPAddressImpl) element).getLocationURI());
                portBinginAttributes.put(DESCRIPTION, HTTP_SERVICE_DESCRIPTION);
                addOperationsName(port.getBinding().getBindingOperations(), portBinginAttributes);
                portBinginNames.put(portBindingsName, portBinginAttributes);
                locations.add(((HTTPAddressImpl) element).getLocationURI());
            }
        });
    }

    /**
     *
     * @param types
     * @param openAPI
     * @return
     */
    private OpenAPITransformer getSchemas(final Types types, final OpenAPI openAPI){
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
     *
     * @param schemaReferences
     * @param resolvedSchemas
     */
    private void resolveIncludedSchemas(List<SchemaReferenceImpl> schemaReferences, final Map<String, Schema> resolvedSchemas){
        schemaReferences.forEach(schema -> resolvedSchemas.putAll(saveSchemas(schema.getReferencedSchema().getElement().getChildNodes())));

    }

    /**
     *
     * @param imports
     * @param resolvedSchemas
     */
    private void resolveImportedSchemas(final Map<String, Vector> imports, final Map<String, Schema> resolvedSchemas){
        imports.forEach((k, v) ->
            v.forEach(vector -> {
                SchemaImportImpl impl = (SchemaImportImpl) vector;
                Optional.ofNullable(impl.getReferencedSchema()).ifPresent(schema -> resolvedSchemas.putAll(saveSchemas(schema.getElement().getChildNodes())));
            }));
    }

    /**
     *
     * @param nodeList
     * @return
     */
    private Map<String, Schema> saveSchemas(final NodeList nodeList){
        final Map<String, Schema> schemas = new HashMap<>();
        for(int i=0; i<nodeList.getLength(); i++) {
            final Node parentNode = nodeList.item(i);
            final Schema childComplexTypeSchema = checkComplexTypeInChildNodes(parentNode.getChildNodes());
            if(null != childComplexTypeSchema){
                Optional.ofNullable(childComplexTypeSchema.getName()).ifPresent(name -> schemas.put(name, childComplexTypeSchema));
            } else if(null != parentNode.getAttributes()) {
                final Schema schema = parseAttributes(parentNode);
                Optional.ofNullable(schema.getName()).ifPresent(name -> schemas.put(name, schema));
            }
        }

        return schemas;
    }

    /**
     *
     * @param nodeList
     * @return
     */
    private Schema checkComplexTypeInChildNodes(final NodeList nodeList){
        for(int i=0; i<nodeList.getLength(); i++){
            Node node = nodeList.item(i);
            if(node.getNodeName().contains("complexType")){
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
     *
     * @param parentNode
     * @return
     */
    private Schema parseAttributes(final Node parentNode){
        final Schema schema = new Schema();
        final NamedNodeMap namedNodeMap = parentNode.getAttributes();
        for(int i=0; i<namedNodeMap.getLength(); i++){
            final Node node = namedNodeMap.item(i);
            if(parentNode.getNodeName().contains("complexType")){
                schema.setType(OBJECT_TYPE);
                parseComplexType(schema, parentNode);
            } else if(parentNode.getNodeName().contains("simpleType")){
                parseSimpleTypeObject(schema, parentNode.getChildNodes());
            }
            setSchemaDefination(schema, node);
        }
        return schema;
    }

    /**
     *
     * @param schema
     * @param complexType
     */
    private void parseComplexType(final Schema schema, final Node complexType){
        NodeList complexTypeChildren = complexType.getChildNodes();
        for(int i=0; i<complexTypeChildren.getLength(); i++){
            Node childNode = complexTypeChildren.item(i);
            if(childNode.getNextSibling() != null){
                Node sibling = childNode.getNextSibling();
                if(sibling.getChildNodes().getLength() > 0){
                    resolveSiblingChildNodes(schema, sibling.getChildNodes());
                }
            }
        }
    }

    /**
     *
     * @param schema
     * @param nodeList
     */
    private void parseSimpleTypeObject(final Schema schema, final NodeList nodeList){
        for(int i=0; i<nodeList.getLength(); i++){
            final Node node = nodeList.item(i);
            if(node.getNodeName().contains("restriction")){
                final NamedNodeMap restrictionNodeMap = node.getAttributes();
                for(int j=0; j<restrictionNodeMap.getLength(); j++){
                    final Node restrictionNode = restrictionNodeMap.item(j);
                    if(restrictionNode.getNodeName().equals("base")){
                        final String type = restrictionNode.getNodeValue();
                        schema.setType(type.substring(type.indexOf(':') + 1));
                    }
                }
            }
        }
    }

    /**
     *
     * @param siblingChilds
     */
    private void resolveSiblingChildNodes(final Schema schema, final NodeList siblingChilds){
        for(int i=0; i<siblingChilds.getLength(); i++){
            Node sequenceNode = siblingChilds.item(i);
            if(sequenceNode.getAttributes() != null && sequenceNode.getAttributes().getLength() > 0){
                resolveSequenceNodeMap(schema, sequenceNode.getAttributes());
            }
        }
    }

    /**
     *
     * @param schema
     * @param sequenceNodeMap
     */
    private void resolveSequenceNodeMap(final Schema schema, final NamedNodeMap sequenceNodeMap){
        for(int i = 0; i<sequenceNodeMap.getLength(); i++){
            Node element = sequenceNodeMap.item(i);
            if(element.getNodeName().equals("type")){
                setRequiredItemsAndPropertiesOnSchema(schema, sequenceNodeMap, element);
            }
        }
    }

    /**
     *
     * @param schema
     * @param namedNodeMap
     * @param node
     */
    private void setRequiredItemsAndPropertiesOnSchema(final Schema schema, final NamedNodeMap namedNodeMap, final Node node){
        for(int j=0; j<namedNodeMap.getLength();j++){
            if(namedNodeMap.item(j).getNodeName().equals("name")){
                schema.addRequiredItem(namedNodeMap.item(j).getNodeValue());
                final String type = node.getNodeValue().substring(node.getNodeValue().indexOf(':') + 1);
                if(null == dataTypes.get(type)){
                    schema.addProperties(namedNodeMap.item(j).getNodeValue(), getReferenceItem(type));
                } else {
                    schema.addProperties(namedNodeMap.item(j).getNodeValue(), getPropertyItem(dataTypes.get(type)));
                }
            }
        }
    }

    /**
     *
     * @param schema
     * @param node
     */
    private void setSchemaDefination(final Schema schema, final Node node){
        if(node.getNodeName().equals("name")){
            schema.setName(node.getNodeValue());
            schema.setTitle(node.getNodeValue());
        } else if(node.getNodeName().equals("type")){
            final String type = node.getNodeValue().substring(node.getNodeValue().indexOf(':') + 1);
            if(null != dataTypes.get(type)){
                schema.setType(dataTypes.get(type));
            } else {
                schema.setType(OBJECT_TYPE);
                schema.addRequiredItem(type);
                schema.addProperties(type, getReferenceItem(type));
            }
        }
    }

    /**
     *
     * @param type
     * @return
     */
    private Schema getReferenceItem(final String type){
        final Schema referenceItem = new Schema();
        referenceItem.set$ref(type);
        return referenceItem;
    }

    /**
     *
     * @param type
     * @return
     */
    private Schema getPropertyItem(final String type){
        final Schema propertyItem = new Schema();
        propertyItem.setType(type);
        return propertyItem;
    }

    /**
     *
     * @param messages
     * @param openAPI
     * @return
     */
    private OpenAPITransformer resolveMessages(final Collection<Message> messages, final OpenAPI openAPI){
        final Map<String, Object> extensions = new HashMap<>();
        final Map<String, Schema> schemaMap = openAPI.getComponents().getSchemas();

        messages.forEach(message -> {
            final String messageName = message.getQName().getLocalPart();
            final Map<String, String> attributes = new HashMap<>();
            attributes.put("$ref", "#/components/schemas/" + messageName);
            extensions.put(messageName, attributes);

            if(null == schemaMap.get(messageName)){
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
     *
     * @param schema
     * @param itemName
     * @param type
     * @param schemaMap
     */
    private void getSchemaForMessages(final Schema schema, final String itemName, final String type, final Map<String, Schema> schemaMap){

        schema.addRequiredItem(itemName);
        Schema propertyItem = new Schema();
        if(null != schemaMap.get(type)){
            propertyItem.set$ref("#/components/schemas/" + type);
        } else {
            propertyItem = getPropertyItem(dataTypes.get(type));
        }
        schema.addProperties(itemName, propertyItem);
    }

    /**
     *
     * @param bindings
     * @param portBindingNames
     * @param openAPI
     */
    private OpenAPITransformer resolveTagsAndSetPaths(final Collection<Binding> bindings, final Map<String, Map<String, Object>> portBindingNames, final OpenAPI openAPI){
        final List<Tag> tagList = new ArrayList<>();
        final Map<String, Map<String, Object>>  discoveredPathsMap = new HashMap<>();
        bindings.forEach(binding -> {
            final String httpMethod = resolveHttpMethod(binding.getExtensibilityElements());
            final Map<String, Object> tagAttributes = portBindingNames.get(binding.getQName().getLocalPart());
            Optional.ofNullable(tagAttributes).ifPresent(attributes -> {
                final Tag tag = new Tag();
                final ExternalDocumentation externalDocs = new ExternalDocumentation();
                tag.setName(binding.getQName().getLocalPart());
                externalDocs.setUrl(attributes.get("url").toString());
                tag.setExternalDocs(externalDocs);
                externalDocs.setDescription((String) attributes.get(DESCRIPTION));
                tagList.add(tag);
            });
            discoverPaths(httpMethod, binding.getBindingOperations(), portBindingNames, discoveredPathsMap);
        });

        openAPI.setTags(tagList);
        setPaths(discoveredPathsMap, openAPI);
        return this;
    }

    /**
     *
     * @param exElements
     * @return
     */
    private String resolveHttpMethod(final List<ExtensibilityElement> exElements){
        String httpMethod = "post";
        for(ExtensibilityElement element :exElements){
            if(element instanceof HTTPBindingImpl){
                httpMethod = ((HTTPBindingImpl) element).getVerb().toLowerCase();
            }
        }
        return httpMethod;
    }

    /**
     *
     * @param httpMethod
     * @param bindingOperations
     * @param portBindingNames
     * @param discoveredPaths
     */
    private void discoverPaths(final String httpMethod, final List<BindingOperationImpl> bindingOperations,
                               final Map<String, Map<String, Object>> portBindingNames, final Map<String, Map<String, Object>> discoveredPaths){
        for(BindingOperationImpl impl :bindingOperations){
            final List<ExtensibilityElement> exList = impl.getExtensibilityElements();
            Map<String, Object> pathAttributes;
            for(ExtensibilityElement element :exList){
                if(element instanceof SOAPOperationImpl || element instanceof SOAP12OperationImpl){
                    pathAttributes = setPathAttributes("SoapService", impl.getName(), httpMethod,
                            impl.getOperation().getInput().getMessage(), impl.getOperation().getOutput().getMessage(),
                            impl.getOperation().getFaults());

                    addPathTags(impl.getName(), "soap", pathAttributes, portBindingNames);
                    discoveredPaths.put(impl.getName(), pathAttributes);
                } else if(element instanceof HTTPOperationImpl){
                    pathAttributes = setPathAttributes("HttpService", impl.getName(), httpMethod,
                            impl.getOperation().getInput().getMessage(), impl.getOperation().getOutput().getMessage(),
                            impl.getOperation().getFaults());
                    addPathTags(impl.getName(), "http", pathAttributes, portBindingNames);
                    discoveredPaths.put(((HTTPOperationImpl) element).getLocationURI(), pathAttributes);
                }
            }
        }
    }

    /**
     *
     * @param description
     * @param operationName
     * @param httpMethod
     * @param inputMessage
     * @param outputMessage
     * @param faultMap
     * @return
     */
    private final Map<String, Object> setPathAttributes(final String description, final String operationName, final String httpMethod,
                                                        final Message inputMessage, final Message outputMessage, final Map<String, Fault> faultMap){
        final Map<String, Object> pathAttributes = new HashMap<>();
        pathAttributes.put(DESCRIPTION, description);
        pathAttributes.put("operation", operationName);
        pathAttributes.put("method", httpMethod);

        Optional.ofNullable(inputMessage).ifPresent(message ->  pathAttributes.put(REQUEST_BODY_KEY, message.getQName().getLocalPart()));
        Optional.ofNullable(outputMessage).ifPresent(message -> pathAttributes.put(RESPONSE_KEY, message.getQName().getLocalPart()));
        pathAttributes.put("faults", !faultMap.isEmpty());

        return pathAttributes;
    }

    /**
     *
     * @param operationName
     * @param requestType
     * @param pathAttributes
     * @param portBindingNames
     */
    private void addPathTags(final String operationName, final String requestType,
                             final Map<String, Object> pathAttributes, final Map<String, Map<String, Object>> portBindingNames){
        portBindingNames.forEach((key, value) -> {
            final Map<String, Object> attributes = value;
            attributes.forEach((k, v) -> {
                if(k.equals("operations")){
                    final List<String> names = (List<String>) v;
                    final List<String> tags = null != pathAttributes.get("tags")
                            ?(List<String>) pathAttributes.get("tags"):new ArrayList<>();
                    final Set<String> locations = null != pathAttributes.get(LOCATIONS_KEY)
                            ?(Set<String>) pathAttributes.get(LOCATIONS_KEY):new HashSet<>();
                    names.forEach(n -> {
                        final String desc = value.get(DESCRIPTION).toString().toLowerCase();
                        if(n.equals(operationName) && desc.contains(requestType)){
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
     *
     * @param nodeList
     * @return
     */
    private String getDocumantation(final NodeList nodeList){
        String data = "";
        for(int i=0; i<nodeList.getLength(); i++){
            final Node node = nodeList.item(i);
            data = Optional.ofNullable(((DeferredTextImpl) node).getData()).orElse("");
        }

        return data;
    }


    /**
     *
     * @param title
     * @param openAPI
     */
    private void setInformation(final String title, final OpenAPI openAPI){
        Info info = new Info();
        info.setTitle(title);
        info.setVersion(DEFAULT_INFORMATION_VERSION);
        openAPI.setInfo(info);
    }

    /**
     *
     * @param bindingOperations
     * @param portBinginAttributes
     */
    private void addOperationsName(final List<BindingOperation> bindingOperations, final Map<String, Object> portBinginAttributes){
        final List<String> names = new ArrayList<>();
        bindingOperations.forEach(operation -> names.add(operation.getName()));
        portBinginAttributes.put("operations", names);
    }

    /**
     *
     * @param discoveredPathsMap
     * @param openAPI
     */
    private void setPaths(final Map<String, Map<String, Object>>  discoveredPathsMap, final OpenAPI openAPI){

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
     *
     * @param bodyName
     * @return
     */
    private RequestBody getRequestBodyForPath(final String bodyName){
        final RequestBody requestBody = new RequestBody();
        requestBody.set$ref("#/components/requestBodies/" + bodyName);
        return requestBody;
    }

    /**
     *
     * @param response
     * @param faults
     * @return
     */
    private ApiResponses getApiResponseForPath(final String response, final boolean faults){
        final ApiResponses apiResponses = new ApiResponses();
        final ApiResponse okResponse = new ApiResponse();
        okResponse.set$ref("#/components/responses/200_" + response);
        apiResponses.addApiResponse(DEFAULT_SUCCESS_RESPONSE_CODE, okResponse);

        if(faults){
            final ApiResponse faultResponse = new ApiResponse();
            faultResponse.set$ref(DEFAULT_FAULT_RESPONSE_REF);
            apiResponses.addApiResponse(DEFAULT_FAULT_RESPONSE_CODE, faultResponse);
        }

        return apiResponses;
    }

    /**
     *
     * @param httpMetod
     * @return
     */
    private PathItem getPathItem(final String httpMetod, final Operation operation){
        final PathItem pathItem = new PathItem();
        switch (httpMetod){
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
     *
     * @param servers
     * @param pathItem
     */
    private void overrideServersIfMoreThanOne(final List<Server> servers, final Set<String> location, final PathItem pathItem){
        if(servers.size() > 1){
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
     *
     * @param portTypes
     * @param openAPI
     * @return
     */
    private OpenAPITransformer addRequestBodiesAndResponses(final Collection<PortType> portTypes, final OpenAPI openAPI){
        final Optional<Map<String, Object>> componentExt = Optional.ofNullable(openAPI.getComponents().getExtensions());
        componentExt.ifPresent(ext -> {
            final Map<String, Object> extensions = (Map<String, Object>) ext.get(MESSAGE_EXTENSIONS);
            for(PortType portType :portTypes){
                final List<javax.wsdl.Operation> operations = portType.getOperations();
                for(javax.wsdl.Operation operation :operations){
                    if(null != operation.getInput().getMessage()) {
                        addRequestBodies(operation.getInput().getMessage().getQName().getLocalPart(), extensions, openAPI);
                    }
                    if(null != operation.getOutput().getMessage()) {
                        addResponses(operation.getOutput().getMessage().getQName().getLocalPart(), extensions, openAPI);
                    }
                    if(!operation.getFaults().isEmpty()){
                        addFaultResponses(operation.getFaults(), openAPI);
                    }
                }
            }
        });

        return this;
    }

    /**
     *
     * @param bodyName
     * @param extensions
     * @param openAPI
     */
    private void addRequestBodies(final String bodyName, final Map<String, Object> extensions, final OpenAPI openAPI){
        if(null != extensions.get(bodyName)){
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
     *
     * @param responseName
     * @param extensions
     * @param openAPI
     */
    private void addResponses(final String responseName, final Map<String, Object> extensions, final OpenAPI openAPI){
        if(null != extensions.get(responseName)){
            openAPI.getComponents().addResponses(String.join("_", DEFAULT_SUCCESS_RESPONSE_CODE, responseName), getResponse(responseName, "Successful operation"));
        }
    }

    /**
     *
     * @param name
     * @param description
     * @return
     */
    private ApiResponse getResponse(final String name, final String description){
        final ApiResponse apiResponse = new ApiResponse();
        final Schema responseSchema = new Schema();
        responseSchema.set$ref(MESSAGE_EXTENSIONS_PREFIX + name);
        apiResponse.setContent(new Content().addMediaType(DEFAULT_CONTENT_TYPE, new MediaType().schema(responseSchema)));
        apiResponse.setDescription(description);

        return apiResponse;
    }

    /**
     *
     * @param faultMap
     * @param openAPI
     */
    private void addFaultResponses(final Map<String, Fault> faultMap, final OpenAPI openAPI){
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
     *
     * @param openAPI
     * @return
     * @throws JsonProcessingException
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

    /**
     *
     * @param definition
     * @param openAPI
     * @return
     */
    private JsonArray checksum(final Definition definition, final OpenAPI openAPI){

        final JsonArray warnings = new JsonArray();
        final Types types = definition.getTypes();
        if(null != types){
            final List<SchemaImpl> exElements = types.getExtensibilityElements();
            exElements.forEach(element -> {
                final List<SchemaReferenceImpl> schemaReferences = element.getIncludes();
                schemaReferences.forEach(schema ->
                        getWarningsForElementNodes(schema.getReferencedSchema().getElement().getChildNodes(), warnings, openAPI));

                final Map<String, Vector> importSchemas = element.getImports();
                importSchemas.forEach((k, v) ->
                        v.forEach(vector -> {
                            SchemaImportImpl impl = (SchemaImportImpl) vector;
                            Optional.ofNullable(impl.getReferencedSchema()).ifPresent(schema ->
                                    getWarningsForElementNodes(schema.getElement().getChildNodes(), warnings, openAPI));
                        }));
                getWarningsForElementNodes(element.getElement().getChildNodes(), warnings, openAPI);
            });
        } else {
            warnings.add("No schema found in wsdl file!");
        }

        final Map<String, HashMap> messages = (Map<String, HashMap>) openAPI.getComponents().getExtensions().get(MESSAGE_EXTENSIONS);

        if(messages.isEmpty()){
            warnings.add("Failed to resolve messages!");
        }
        messages.forEach((k, v) -> {
            if(!openAPI.getComponents().getSchemas().containsKey(k)){
                warnings.add("Failed to resolve " + k + " scheme!");
            }
        });

        if(openAPI.getServers().isEmpty()){
            warnings.add("Failed to resolve services!");
        }

        if(openAPI.getPaths().isEmpty()){
            warnings.add("Failed to resolve paths!");
        }

        return warnings;
    }

    /**
     *
     * @param nodeList
     * @param warnings
     * @param openAPI
     */
    private void getWarningsForElementNodes(final NodeList nodeList, final JsonArray warnings, final OpenAPI openAPI){
        for(int i=0; i<nodeList.getLength(); i++){
            Node node = nodeList.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE){
                NamedNodeMap list = node.getAttributes();
                for(int j=0; j<list.getLength(); j++){
                    Node jNode = list.item(j);
                    if(jNode.getNodeName().equals("name")
                            && !openAPI.getComponents().getSchemas().containsKey(jNode.getNodeValue())){
                        warnings.add("Failed to resolve " + jNode.getNodeValue() + " scheme!");
                    }
                }
            }

        }
    }
}
