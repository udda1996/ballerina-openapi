/*
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.com). All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.openapi.core.generators.schema;

import io.ballerina.compiler.syntax.tree.AbstractNodeFactory;
import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.RecordTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.compiler.syntax.tree.TypeDefinitionNode;
import io.ballerina.compiler.syntax.tree.TypeReferenceNode;
import io.ballerina.openapi.core.GeneratorConstants;
import io.ballerina.openapi.core.GeneratorUtils;
import io.ballerina.openapi.core.exception.BallerinaOpenApiException;
import io.ballerina.openapi.core.generators.schema.ballerinatypegenerators.AllOfRecordTypeGenerator;
import io.ballerina.openapi.core.generators.schema.ballerinatypegenerators.ArrayTypeGenerator;
import io.ballerina.openapi.core.generators.schema.ballerinatypegenerators.RecordTypeGenerator;
import io.ballerina.openapi.core.generators.schema.ballerinatypegenerators.TypeGenerator;
import io.ballerina.openapi.core.generators.schema.ballerinatypegenerators.UnionTypeGenerator;
import io.ballerina.openapi.core.generators.schema.model.GeneratorMetaData;
import io.ballerina.tools.text.TextDocument;
import io.ballerina.tools.text.TextDocuments;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static io.ballerina.compiler.syntax.tree.AbstractNodeFactory.createEmptyNodeList;
import static io.ballerina.compiler.syntax.tree.AbstractNodeFactory.createNodeList;
import static io.ballerina.openapi.converter.Constants.HTTP;

/**
 * This class wraps the {@link Schema} from openapi models inorder to overcome complications
 * while populating syntax tree.
 *
 * @since 1.3.0
 */
public class BallerinaTypesGenerator {

    private List<TypeDefinitionNode> typeDefinitionNodeList;
    private boolean hasConstraints;

    /**
     * This public constructor is used to generate record and other relevant data type when the nullable flag is
     * enabled in the openapi command.
     *
     * @param openAPI    OAS definition
     * @param isNullable nullable value
     */
    public BallerinaTypesGenerator(OpenAPI openAPI, boolean isNullable) {
        GeneratorMetaData.createInstance(openAPI, isNullable);
        this.typeDefinitionNodeList = new LinkedList<>();
        this.hasConstraints = false;
    }

    /**
     * This public constructor is used to generate record and other relevant data type when the absent of the nullable
     * flag in the openapi command.
     *
     * @param openAPI OAS definition
     */
    public BallerinaTypesGenerator(OpenAPI openAPI) {
        this(openAPI, false);
        GeneratorMetaData.createInstance(openAPI, false);
    }

    /**
     * Set the typeDefinitionNodeList.
     */
    public void setTypeDefinitionNodeList(List<TypeDefinitionNode> typeDefinitionNodeList) {
        this.typeDefinitionNodeList = typeDefinitionNodeList;
    }

    /**
     * Generate syntaxTree for component schema.
     */
    public SyntaxTree generateSyntaxTree() throws BallerinaOpenApiException {
        OpenAPI openAPI = GeneratorMetaData.getInstance().getOpenAPI();
        List<TypeDefinitionNode> typeDefinitionNodeListForSchema = new ArrayList<>();
        if (openAPI.getComponents() != null) {
            // Create typeDefinitionNode
            Components components = openAPI.getComponents();
            Map<String, Schema> schemas = components.getSchemas();
            if (schemas != null) {
                for (Map.Entry<String, Schema> schema : schemas.entrySet()) {
                    String schemaKey = schema.getKey().trim();
                    if (!hasConstraints) {
                        hasConstraints = GeneratorUtils.hasConstraints(schema.getValue());
                    }
                    if (GeneratorUtils.isValidSchemaName(schemaKey)) {
                        List<Node> schemaDoc = new ArrayList<>();
                        typeDefinitionNodeListForSchema.add(getTypeDefinitionNode
                                (schema.getValue(), schemaKey, schemaDoc));
                    }
                }
            }
        }
        //Create imports for the http module, when record has http type inclusions.
        NodeList<ImportDeclarationNode> imports = generateImportNodes();
        typeDefinitionNodeList.addAll(typeDefinitionNodeListForSchema);
        // Create module member declaration
        NodeList<ModuleMemberDeclarationNode> moduleMembers = AbstractNodeFactory.createNodeList(
                typeDefinitionNodeList.toArray(new TypeDefinitionNode[typeDefinitionNodeList.size()]));

        Token eofToken = AbstractNodeFactory.createIdentifierToken("");
        ModulePartNode modulePartNode = NodeFactory.createModulePartNode(imports, moduleMembers, eofToken);

        TextDocument textDocument = TextDocuments.from("");
        SyntaxTree syntaxTree = SyntaxTree.from(textDocument);
        return syntaxTree.modifyWith(modulePartNode);
    }

    private NodeList<ImportDeclarationNode> generateImportNodes() {
        List<ImportDeclarationNode> imports = new ArrayList<>();
        if (!typeDefinitionNodeList.isEmpty()) {
            importsForTypeDefinitions(imports);
        }
        boolean nullable = GeneratorMetaData.getInstance().isNullable();
        if (hasConstraints && !nullable) {
            //import for constraint
            ImportDeclarationNode importForConstraint = GeneratorUtils.getImportDeclarationNode(
                    GeneratorConstants.BALLERINA,
                    GeneratorConstants.CONSTRAINT);
            imports.add(importForConstraint);
        }
        if (imports.isEmpty()) {
            return createEmptyNodeList();
        }
        return createNodeList(imports);
    }

    private void importsForTypeDefinitions(List<ImportDeclarationNode> imports) {
        for (TypeDefinitionNode node : typeDefinitionNodeList) {
            if (!(node.typeDescriptor() instanceof RecordTypeDescriptorNode)) {
                continue;
            }
            RecordTypeDescriptorNode record = (RecordTypeDescriptorNode) node.typeDescriptor();
            for (Node field : record.fields()) {
                if (!(field instanceof TypeReferenceNode) ||
                        !(((TypeReferenceNode) field).typeName() instanceof QualifiedNameReferenceNode)) {
                    continue;
                }
                TypeReferenceNode recordField = (TypeReferenceNode) field;
                QualifiedNameReferenceNode typeInclusion = (QualifiedNameReferenceNode) recordField.typeName();
                boolean isHttpImportExist = imports.stream().anyMatch(importNode -> importNode.moduleName().stream()
                        .anyMatch(moduleName -> moduleName.text().equals(HTTP)));

                if (!isHttpImportExist && typeInclusion.modulePrefix().text().equals(HTTP)) {
                    ImportDeclarationNode importForHttp = GeneratorUtils.getImportDeclarationNode(
                            GeneratorConstants.BALLERINA,
                            GeneratorConstants.HTTP);
                    imports.add(importForHttp);
                    break;
                }
            }
        }
    }

    /**
     * Create Type Definition Node for a given OpenAPI schema.
     *
     * @param schema   OpenAPI schema
     * @param typeName IdentifierToken of the name of the type
     * @return {@link TypeDefinitionNode}
     * @throws BallerinaOpenApiException when unsupported schema type is found
     */
    public TypeDefinitionNode getTypeDefinitionNode(Schema schema, String typeName, List<Node> schemaDocs)
            throws BallerinaOpenApiException {
        IdentifierToken typeNameToken = AbstractNodeFactory.createIdentifierToken(GeneratorUtils.getValidName(
                typeName.trim(), true));
        TypeGenerator typeGenerator = TypeGeneratorUtils.getTypeGenerator(schema, GeneratorUtils.getValidName(
                typeName.trim(), true), null);
        List<AnnotationNode> typeAnnotations = new ArrayList<>();
        AnnotationNode constraintNode = TypeGeneratorUtils.generateConstraintNode(schema);
        if (constraintNode != null) {
            typeAnnotations.add(constraintNode);
        }
        TypeGeneratorUtils.getRecordDocs(schemaDocs, schema, typeAnnotations);
        TypeDefinitionNode typeDefinitionNode =
                typeGenerator.generateTypeDefinitionNode(typeNameToken, schemaDocs, typeAnnotations);

        if (typeGenerator instanceof ArrayTypeGenerator &&
                ((ArrayTypeGenerator) typeGenerator).getArrayItemWithConstraint() != null) {
            typeDefinitionNodeList.add(((ArrayTypeGenerator) typeGenerator).getArrayItemWithConstraint());
        } else if (typeGenerator instanceof RecordTypeGenerator &&
                !((RecordTypeGenerator) typeGenerator).getTypeDefinitionNodeList().isEmpty()) {
            removeDuplicateNode(((RecordTypeGenerator) typeGenerator).getTypeDefinitionNodeList());
        } else if (typeGenerator instanceof AllOfRecordTypeGenerator &&
                !((AllOfRecordTypeGenerator) typeGenerator).getTypeDefinitionNodeList().isEmpty()) {
            removeDuplicateNode(((AllOfRecordTypeGenerator) typeGenerator).getTypeDefinitionNodeList());
        } else if (typeGenerator instanceof UnionTypeGenerator &&
                !((UnionTypeGenerator) typeGenerator).getTypeDefinitionNodeList().isEmpty()) {
            removeDuplicateNode(((UnionTypeGenerator) typeGenerator).getTypeDefinitionNodeList());
        }
        return typeDefinitionNode;
    }

    /**
     * Remove duplicate of the TypeDefinitionNode.
     */
    private void removeDuplicateNode(List<TypeDefinitionNode> newConstraintNode) {

        for (TypeDefinitionNode newNode : newConstraintNode) {
            boolean isExist = false;
            for (TypeDefinitionNode oldNode : typeDefinitionNodeList) {
                if (newNode.typeName().text().equals(oldNode.typeName().text())) {
                    isExist = true;
                    break;
                }
            }
            if (!isExist) {
                typeDefinitionNodeList.add(newNode);
            }
        }
    }
}