package com.zorth.aiplatform.semantic.scan;

import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.model.SqlOperation;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public final class MapperXmlPreflight {

    public MapperPreflightResult inspect(String xml) {
        Objects.requireNonNull(xml, "xml must not be null");
        Document document = parse(xml);
        Element root = document.getDocumentElement();
        if (root == null || !"mapper".equals(localName(root))) {
            throw xmlError("The XML document is not a MyBatis mapper");
        }
        String namespace = root.getAttribute("namespace");
        if (namespace == null || namespace.isBlank()) {
            throw xmlError("The mapper namespace is missing");
        }
        List<MapperPreflightResult.MapperStatementRef> statements = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            SqlOperation operation = operationFor(localName(element));
            if (operation == null) {
                continue;
            }
            String id = element.getAttribute("id");
            if (id == null || id.isBlank()) {
                throw xmlError("A mapper statement is missing an id");
            }
            if (!ids.add(id)) {
                throw xmlError("The mapper contains duplicate statement ids");
            }
            statements.add(new MapperPreflightResult.MapperStatementRef(id, operation));
        }
        return new MapperPreflightResult(namespace.trim(), statements);
    }

    private static SqlOperation operationFor(String localName) {
        return switch (localName.toLowerCase(Locale.ROOT)) {
            case "select" -> SqlOperation.SELECT;
            case "insert" -> SqlOperation.INSERT;
            case "update" -> SqlOperation.UPDATE;
            case "delete" -> SqlOperation.DELETE;
            default -> null;
        };
    }

    private static String localName(Element element) {
        String localName = element.getLocalName();
        if (localName != null && !localName.isBlank()) {
            return localName;
        }
        String tag = element.getTagName();
        int colon = tag.indexOf(':');
        return colon >= 0 ? tag.substring(colon + 1) : tag;
    }

    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) ->
                    new InputSource(new ByteArrayInputStream(new byte[0])));
            try (InputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
                InputSource source = new InputSource(input);
                source.setEncoding(StandardCharsets.UTF_8.name());
                return builder.parse(source);
            }
        }
        catch (ParserConfigurationException ex) {
            throw xmlError("The XML parser could not be configured", ex);
        }
        catch (SAXException | IOException ex) {
            throw xmlError("The Mapper XML is malformed or unsupported", ex);
        }
    }

    private static MapperSemanticExtractionException xmlError(String message) {
        return new MapperSemanticExtractionException(MapperSemanticFailureType.XML_VALIDATION_ERROR, message);
    }

    private static MapperSemanticExtractionException xmlError(String message, Throwable cause) {
        return new MapperSemanticExtractionException(
                MapperSemanticFailureType.XML_VALIDATION_ERROR, message, cause);
    }
}
