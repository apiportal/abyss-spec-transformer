[![Build Status](https://travis-ci.org/apiportal/abyss-spec-transformer.svg?branch=master)](https://travis-ci.org/apiportal/abyss-spec-transformer)

# ABYSS SPEC TRANSFORMER

This application transforms provided `WSDL` into an `OpenAPI v3` spec yaml file. WSDL should be provided via path, URI or String  

An example is below

**Input**

```xml
<?xml version="1.0" standalone="no"?>
<wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                  xmlns="http://schemas.xmlsoap.org/wsdl/"
                  xmlns:wsaw="http://www.w3.org/2006/05/addressing/wsdl"
                  xmlns:http="http://schemas.xmlsoap.org/wsdl/http/"
                  xmlns:tns="http://thomas-bayer.com/blz/"
                  xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                  xmlns:mime="http://schemas.xmlsoap.org/wsdl/mime/"
                  xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                  xmlns:soap12="http://schemas.xmlsoap.org/wsdl/soap12/"
                  targetNamespace="http://thomas-bayer.com/blz/">
    <wsdl:documentation>BLZService</wsdl:documentation>
    <wsdl:types>
        <xsd:schema attributeFormDefault="unqualified" elementFormDefault="qualified" targetNamespace="http://thomas-bayer.com/blz/">
            <xsd:element name="getBank" type="tns:getBankType"></xsd:element>
            <xsd:element name="getBankResponse" type="tns:getBankResponseType"></xsd:element>
            <xsd:complexType name="getBankType">
                <xsd:sequence>
                    <xsd:element name="blz" type="xsd:string"></xsd:element>
                </xsd:sequence>
            </xsd:complexType>
            <xsd:complexType name="getBankResponseType">
                <xsd:sequence>
                    <xsd:element name="details" type="tns:detailsType"></xsd:element>
                </xsd:sequence>
            </xsd:complexType>
            <xsd:complexType name="detailsType">
                <xsd:sequence>
                    <xsd:element minOccurs="0" name="bezeichnung" type="xsd:string"></xsd:element>
                    <xsd:element minOccurs="0" name="bic" type="xsd:string"></xsd:element>
                    <xsd:element minOccurs="0" name="ort" type="xsd:string"></xsd:element>
                    <xsd:element minOccurs="0" name="plz" type="xsd:string"></xsd:element>
                </xsd:sequence>
            </xsd:complexType>
        </xsd:schema>
    </wsdl:types>
    <wsdl:message name="getBank">
        <wsdl:part name="parameters" element="tns:getBank"></wsdl:part>
    </wsdl:message>
    <wsdl:message name="getBankResponse">
        <wsdl:part name="parameters" element="tns:getBankResponse"></wsdl:part>
    </wsdl:message>
    <wsdl:portType name="BLZServicePortType">
        <wsdl:operation name="getBank">
            <wsdl:input message="tns:getBank"></wsdl:input>
            <wsdl:output message="tns:getBankResponse" wsaw:Action="http://thomas-bayer.com/blz/BLZService/getBankResponse"></wsdl:output>
        </wsdl:operation>
    </wsdl:portType>
    <wsdl:binding name="BLZServiceSOAP11Binding" type="tns:BLZServicePortType">
        <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"></soap:binding>
        <wsdl:operation name="getBank">
            <soap:operation style="document" soapAction=""></soap:operation>
            <wsdl:input>
                <soap:body use="literal"></soap:body>
            </wsdl:input>
            <wsdl:output>
                <soap:body use="literal"></soap:body>
            </wsdl:output>
        </wsdl:operation>
    </wsdl:binding>
    <wsdl:binding name="BLZServiceSOAP12Binding" type="tns:BLZServicePortType">
        <soap12:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"></soap12:binding>
        <wsdl:operation name="getBank">
            <soap12:operation style="document" soapAction=""></soap12:operation>
            <wsdl:input>
                <soap12:body use="literal"></soap12:body>
            </wsdl:input>
            <wsdl:output>
                <soap12:body use="literal"></soap12:body>
            </wsdl:output>
        </wsdl:operation>
    </wsdl:binding>
    <wsdl:binding name="BLZServiceHttpBinding" type="tns:BLZServicePortType">
        <http:binding verb="POST"></http:binding>
        <wsdl:operation name="getBank">
            <http:operation location="BLZService/getBank"></http:operation>
            <wsdl:input>
                <mime:content part="getBank" type="text/xml"></mime:content>
            </wsdl:input>
            <wsdl:output>
                <mime:content part="getBank" type="text/xml"></mime:content>
            </wsdl:output>
        </wsdl:operation>
    </wsdl:binding>
    <wsdl:service name="BLZService">
        <wsdl:port name="BLZServiceSOAP11port_http" binding="tns:BLZServiceSOAP11Binding">
            <soap:address location="http://www.thomas-bayer.com/axis2/services/BLZService"></soap:address>
        </wsdl:port>
        <wsdl:port name="BLZServiceSOAP12port_http" binding="tns:BLZServiceSOAP12Binding">
            <soap12:address location="http://www.thomas-bayer.com/axis2/services/BLZService"></soap12:address>
        </wsdl:port>
        <wsdl:port name="BLZServiceHttpport" binding="tns:BLZServiceHttpBinding">
            <http:address location="http://www.thomas-bayer.com/axis2/services/BLZService"></http:address>
        </wsdl:port>
    </wsdl:service>
</wsdl:definitions>
```

>credit to http://www.thomas-bayer.com/


**Usage**

```java
String sourcePath = "http://thomas-bayer.com/axis2/services/BLZService?wsdl";
return new OpenAPITransformer().transform(sourcePath);        
```

**Output**

```yaml
openapi: 3.0.1
info:
  title: BLZService
  version: "1.0"
servers:
- url: http://www.thomas-bayer.com/axis2/services/BLZService
tags:
- name: BLZServiceSOAP11Binding
  externalDocs:
    description: Soap 1.1 Service
    url: http://www.thomas-bayer.com/axis2/services/BLZService
- name: BLZServiceHttpBinding
  externalDocs:
    description: HTTP Service
    url: http://www.thomas-bayer.com/axis2/services/BLZService
- name: BLZServiceSOAP12Binding
  externalDocs:
    description: Soap 1.2 Service
    url: http://www.thomas-bayer.com/axis2/services/BLZService
paths:
  /BLZService/getBank:
    post:
      tags:
      - BLZServiceHttpBinding
      operationId: getBank_HttpService
      requestBody:
        $ref: '#/components/requestBodies/getBank'
      responses:
        200:
          $ref: '#/components/responses/200_getBankResponse'
  /getBank:
    post:
      tags:
      - BLZServiceSOAP12Binding
      - BLZServiceSOAP11Binding
      operationId: getBank_SoapService
      requestBody:
        $ref: '#/components/requestBodies/getBank'
      responses:
        200:
          $ref: '#/components/responses/200_getBankResponse'
components:
  schemas:
    getBankResponseType:
      title: getBankResponseType
      required:
      - details
      type: object
      properties:
        details:
          $ref: '#/components/schemas/detailsType'
    getBankResponse:
      title: getBankResponse
      required:
      - getBankResponseType
      type: object
      properties:
        getBankResponseType:
          $ref: '#/components/schemas/getBankResponseType'
    getBankType:
      title: getBankType
      required:
      - blz
      type: object
      properties:
        blz:
          type: string
    detailsType:
      title: detailsType
      required:
      - bezeichnung
      - bic
      - ort
      - plz
      type: object
      properties:
        bezeichnung:
          type: string
        bic:
          type: string
        ort:
          type: string
        plz:
          type: string
    getBank:
      title: getBank
      required:
      - getBankType
      type: object
      properties:
        getBankType:
          $ref: '#/components/schemas/getBankType'
  responses:
    200_getBankResponse:
      description: Successful operation
      content:
        application/xml:
          schema:
            $ref: '#/components/x-messages/getBankResponse'
  requestBodies:
    getBank:
      description: getBank message object as request body
      content:
        application/xml:
          schema:
            $ref: '#/components/x-messages/getBank'
      required: true
  x-messages:
    getBankResponse:
      $ref: '#/components/schemas/getBankResponse'
    getBank:
      $ref: '#/components/schemas/getBank'
```