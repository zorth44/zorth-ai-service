package com.zorth.aiplatform.semantic.scan;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class MapperFixtureResourcesTest {

    @Test
    void providesRequiredMapperCategories() {
        List<String> resources = List.of(
                "mappers/ordinary-select/OrdinarySelectMapper.xml",
                "mappers/left-join/LeftJoinMapper.xml",
                "mappers/dynamic-if/DynamicIfMapper.xml",
                "mappers/local-include/LocalIncludeMapper.xml",
                "mappers/dynamic-update/DynamicUpdateMapper.xml",
                "mappers/mixed-ops/MixedOperationsMapper.xml",
                "mappers/unresolved-include/UnresolvedIncludeMapper.xml",
                "mappers/doctype-cdata/DoctypeCdataMapper.xml",
                "mappers/choose-when/ChooseWhenMapper.xml",
                "mappers/module-a/mapper/UserMapper.xml",
                "mappers/module-b/mapper/UserMapper.xml",
                "mappers/malformed/BrokenMapper.xml",
                "mappers/non-mapper/not-a-mapper.xml",
                "mappers/xxe/ExternalEntityMapper.xml");
        for (String resource : resources) {
            assertTrue(new ClassPathResource(resource).exists(), resource);
        }
    }
}
