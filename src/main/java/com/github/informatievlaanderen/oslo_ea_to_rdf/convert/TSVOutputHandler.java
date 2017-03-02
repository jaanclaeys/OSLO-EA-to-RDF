package com.github.informatievlaanderen.oslo_ea_to_rdf.convert;

import com.github.informatievlaanderen.oslo_ea_to_rdf.ea.*;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDFS;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Class that aggregates the conversion results in tab separated value file.
 *
 * @author Dieter De Paepe
 */
public class TSVOutputHandler implements OutputHandler {
    private final static Joiner JOINER = Joiner.on(", ");

    private BufferedWriter writer;
    private TagHelper tagHelper;
    private EADiagram diagram;
    private List<String> tagNames;

    public TSVOutputHandler(BufferedWriter writer, TagHelper tagHelper, EADiagram diagram) throws IOException {
        this.writer = writer;
        this.tagHelper = tagHelper;
        this.diagram = diagram;
        this.tagNames = tagHelper.getTagNamesFor(Scope.FULL_DEFINITON);

        write("EA-Type");
        write("EA-Package");
        write("EA-Name");
        write("EA-Parent");
        write("EA-Domain");
        write("EA-Range");

        for (String tagName : tagNames) {
            write(tagName);
        }

        write("external term");
        write("namespace");
        write("localname");
        write("type");
        write("domain");
        write("range");
        writeNl("parent");
    }

    private DiagramElement findInDiagram(EAElement element) {
        for (DiagramElement diagramElement : diagram.getElements()) {
            if (diagramElement.getReferencedElement().equals(element))
                return diagramElement;
        }
        return null;
    }

    private DiagramConnector findInDiagram(EAConnector connector) {
        for (DiagramElement element : diagram.getElements()) {
            for (DiagramConnector rawConnector : element.getConnectors()) {
                for (EAConnector conn : Util.extractAssociationElement(rawConnector.getReferencedConnector(), rawConnector.getLabelDirection())) {
                    if (connector.equals(conn))
                        return rawConnector;
                }
            }
        }
        return null;
    }

    private List<String> extactTagValues(List<TagData> tagData) {
        List<String> result = new ArrayList<>();

        for (String tagName : tagNames) {
            String s = tagData.stream()
                    .filter(t -> tagName.equals(t.getOriginTag()))
                    .findFirst()
                    .map(t -> t.getValue().getString())
                    .orElse("");
            result.add(s);
        }
        return result;
    }

    @Override
    public void handleOntology(EAPackage sourcePackage, Resource ontology, String prefix, String baseURI) {
        write("Package");
        write("");
        write(sourcePackage.getName());
        write("");
        write("");
        write("");

        for (String s : tagHelper.getTagNamesFor(Scope.FULL_DEFINITON)) {
            write("");
        }

        write("");
        write(ontology.getNameSpace());
        write(ontology.getLocalName());
        write(OWL.Ontology.getURI());
        write("");
        write("");
        writeNl("");
    }

    @Override
    public void handleClass(EAElement sourceElement, Resource clazz, Scope scope,
                            Resource ontology, List<Resource> parentClasses, List<Resource> allowedValues) {
        write(sourceElement.getType().toString());
        write(sourceElement.getPackage().getName());
        write(sourceElement.getName());

        write(JOINER.join(findParents(findInDiagram(sourceElement))));
        write("");
        write("");

        for (String tag : extactTagValues(tagHelper.getTagDataFor(sourceElement, Scope.FULL_DEFINITON))) {
            write(tag);
        }

        write(String.valueOf(scope != Scope.FULL_DEFINITON));
        write(clazz.getNameSpace());
        write(clazz.getLocalName());
        write(RDFS.Class.getURI());
        write("");
        write("");
        writeNl(JOINER.join(parentClasses));
    }

    @Override
    public void handleProperty(PropertySource source, Resource property, Scope scope,
                               Resource ontology, Resource propertyType, Resource domain, Resource range,
                               List<Resource> superProperties) {
        if (source.attribute != null) {
            write("attribute");
            write(source.attribute.getElement().getPackage().getName());
            write(source.attribute.getName());
            write("");
            write(source.attribute.getElement().getName());
            write(source.attribute.getType());
        } else {
            write("connector");
            write("");
            write(source.connector.getName());
            write("");
            DiagramConnector dConnector = findInDiagram(source.connector);
            if (EAConnector.Direction.SOURCE_TO_DEST.equals(dConnector.getLabelDirection())) {
                write(source.connector.getSource().getName());
                write(source.connector.getDestination().getName());
            } else if (EAConnector.Direction.DEST_TO_SOURCE.equals(dConnector.getLabelDirection())) {
                write(source.connector.getDestination().getName());
                write(source.connector.getSource().getName());
            } else {
                write("");
                write("");
            }
        }

        for (String tag : extactTagValues(tagHelper.getTagDataFor(MoreObjects.firstNonNull(source.attribute, source.connector), Scope.FULL_DEFINITON))) {
            write(tag);
        }

        write(Boolean.toString(scope != Scope.FULL_DEFINITON));
        write(property.getNameSpace());
        write(property.getLocalName());
        write(propertyType.getURI());
        write(domain != null ? domain.getURI() : "");
        write(range != null ? range.getURI() : "");
        writeNl(JOINER.join(Iterables.transform(superProperties, Resource::getURI)));
    }

    @Override
    public void handleInstance(EAAttribute source, Resource instance, Scope scope,
                               Resource ontology, Resource clazz) {
        write("attribute");
        write(source.getElement().getPackage().getName());
        write(source.getName());
        write("");
        write(source.getElement().getName());
        write("");

        for (String tag : extactTagValues(tagHelper.getTagDataFor(source, Scope.FULL_DEFINITON))) {
            write(tag);
        }

        write(Boolean.toString(scope != Scope.FULL_DEFINITON));
        write(instance.getNameSpace());
        write(instance.getURI());
        write(clazz.getURI());
        write("");
        write("");
        writeNl("");
    }

    private List<EAElement> findParents(DiagramElement child) {
        return child.getConnectors().stream()
                .map(dConn -> {
                    EAConnector conn = dConn.getReferencedConnector();
                    if (!EAConnector.TYPE_GENERALIZATION.equals(conn.getType())
                            || Boolean.valueOf(Util.getOptionalTag(conn, TagNames.IGNORE, "false"))
                            || dConn.isHidden()
                            || EAConnector.Direction.BIDIRECTIONAL.equals(conn.getDirection())
                            || EAConnector.Direction.UNSPECIFIED.equals(conn.getDirection()))
                        return null;
                    if (EAConnector.Direction.SOURCE_TO_DEST.equals(conn.getDirection())) {
                        if (child.getReferencedElement().equals(conn.getSource()))
                            return conn.getDestination();
                        else
                            return null;
                    } else {
                        if (child.getReferencedElement().equals(conn.getDestination()))
                            return conn.getSource();
                        else
                            return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void write(String s) {
        try {
            writer.write(Strings.nullToEmpty(s));
            writer.write("\t");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

     private void writeNl(String s) {
         try {
             writer.write(Strings.nullToEmpty(s));
             writer.write("\n");
         } catch (IOException e) {
             throw new RuntimeException(e);
         }
     }
}
