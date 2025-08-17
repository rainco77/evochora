package org.evochora.compiler.backend.layout;

import org.evochora.compiler.api.PlacedMolecule;
import org.evochora.compiler.api.SourceInfo;

import java.util.List;
import java.util.Map;

/**
 * Result of the layout phase (without linking): holds coordinates and mappings.
 */
public record LayoutResult(
        Map<Integer, int[]> linearAddressToCoord,
        Map<String, Integer> relativeCoordToLinearAddress, // GEÄNDERT
        Map<String, Integer> labelToAddress,
        Map<Integer, SourceInfo> sourceMap,
        Map<int[], PlacedMolecule> initialWorldObjects
) {}
