package com.comac.rpm.modules.file;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TransformFilePolicyTest {
    @Test void rejectsExternalGenericAndAmbiguousReferences() {
        for(String url:new String[]{"https://evil.invalid/file", "/api/files/download?objectKey=evidence/fake", "/api/files/transform/download?fileId=../secret", TransformFilePolicy.URL_PREFIX+"123e4567-e89b-12d3-a456-426614174000&projectId=2"})
            assertThrows(RuntimeException.class,()->TransformFilePolicy.fileId(url));
    }
    @Test void acceptsOnlyCanonicalRegistryReference() {
        String id="123e4567-e89b-12d3-a456-426614174000";
        assertEquals(id,TransformFilePolicy.fileId(TransformFilePolicy.URL_PREFIX+id));
    }
}
