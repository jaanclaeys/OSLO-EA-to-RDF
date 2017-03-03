package com.github.informatievlaanderen.oslo_ea_to_rdf.ea;

import com.google.common.collect.ListMultimap;

/**
 * An object used in Enterprise Architect for modelling purposes.
 */
public interface EAObject {
    /**
     * Gets the name of the object.
     *
     * @return the name, or {@code null}
     */
    String getName();

    /**
     * Gets the notes of this object.
     *
     * @return the notes, or {@code null}
     */
    String getNotes();

    /**
     * Gets the GUID of this object.
     *
     * @return the GUID, or {@code null}
     */
    String getGuid();

    /**
     * Gets the tags linked to this object.
     *
     * @return an unmodifiable map
     */
    ListMultimap<String, String> getTags();
}