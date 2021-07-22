/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */


package io.ballerina.openapi.generators.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.swagger.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAPIServiceMapper provides functionality for reading and writing OpenApi, either to and from ballerina service, or
 * to, as well as related functionality for performing conversions between openapi and ballerina.
 */
public class OpenAPIServiceMapper {
    private static final Logger logger = LoggerFactory.getLogger(
            OpenAPIServiceMapper.class);
    private  SemanticModel semanticModel;
    private OpenAPIEndpointMapper openApiEndpointMapper;

    public SemanticModel getSemanticModel() {
        return semanticModel;
    }

    public void setSemanticModel(SemanticModel semanticModel) {
        this.semanticModel = semanticModel;
    }

    private ObjectMapper objectMapper;


    /**
     * Initializes a service parser for OpenApi.
     */
    public OpenAPIServiceMapper(OpenAPIEndpointMapper openApiEndpointMapper) {
        // Default object mapper is JSON mapper available in openApi utils.
        this.objectMapper = Json.mapper();
        this.openApiEndpointMapper = openApiEndpointMapper;
    }

    /**
     * This method will convert ballerina @Service to OpenApi @OpenApi object.
     *
     * @param service ballerina @Service object to be map to openapi definition
     * @return OpenApi object which represent current service.
     */
    public OpenAPI convertServiceToOpenApi(ServiceDeclarationNode service) {
        OpenAPI openapi = new OpenAPI();
        String currentServiceName = openApiEndpointMapper.getServiceBasePath(service);
        return convertServiceToOpenApi(service, openapi, currentServiceName);
    }

    /**
     * This method will convert ballerina @Service to openApi @OpenApi object.
     *
     * @param service   - Ballerina @Service object to be map to openApi definition
     * @param openapi   - OpenApi model to populate
     * @param basePath  - For string base path
     * @return OpenApi object which represent current service.
     */
    public OpenAPI convertServiceToOpenApi(ServiceDeclarationNode service, OpenAPI openapi, String basePath) {
        // Setting default values.
        openapi.setInfo(new io.swagger.v3.oas.models.info.Info().version("1.0.0").title(basePath.replace("/", " ")));

        NodeList<Node> functions = service.members();
        List<FunctionDefinitionNode> resource = new ArrayList<>();
        for (Node function: functions) {
            SyntaxKind kind = function.kind();
            if (kind.equals(SyntaxKind.RESOURCE_ACCESSOR_DEFINITION)) {
                resource.add((FunctionDefinitionNode) function);
            }
        }
        OpenAPIResourceMapper resourceMapper = new OpenAPIResourceMapper(this.semanticModel);
        openapi.setPaths(resourceMapper.convertResourceToPath(resource));
        openapi.setComponents(resourceMapper.getComponents());
        return openapi;
    }

}