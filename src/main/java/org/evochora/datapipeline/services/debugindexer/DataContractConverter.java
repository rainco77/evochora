package org.evochora.datapipeline.services.debugindexer;

import org.evochora.compiler.api.PlacedMolecule;
import org.evochora.compiler.api.SourceInfo;
import org.evochora.datapipeline.api.contracts.*;
import org.evochora.server.contracts.raw.SerializableProcFrame;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.stream.Collectors;

public class DataContractConverter {

    public static org.evochora.compiler.api.ProgramArtifact convertProgramArtifact(org.evochora.datapipeline.api.contracts.ProgramArtifact apiArtifact) {
        return new org.evochora.compiler.api.ProgramArtifact(
                apiArtifact.getProgramId(),
                apiArtifact.getSources(),
                apiArtifact.getMachineCodeLayout(),
                apiArtifact.getInitialWorldObjects().entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> e.getKey(),
                                e -> convertPlacedMolecule(e.getValue())
                        )),
                apiArtifact.getSourceMap().entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> e.getKey(),
                                e -> convertSourceInfo(e.getValue())
                        )),
                null, // callSiteBindings
                null, // relativeCoordToLinearAddress
                null, // linearAddressToCoord
                apiArtifact.getLabelAddressToName(),
                null, // registerAliasMap
                null, // procNameToParamNames
                null, // tokenMap
                null // tokenLookup
        );
    }

    public static org.evochora.server.contracts.raw.RawTickState convertToRawTickState(RawTickData rawTickData) {
        return new org.evochora.server.contracts.raw.RawTickState(
                rawTickData.getTickNumber(),
                rawTickData.getOrganisms().stream().map(DataContractConverter::convertOrganismState).collect(Collectors.toList()),
                rawTickData.getCells().stream().map(DataContractConverter::convertCellState).collect(Collectors.toList())
        );
    }

    public static org.evochora.server.contracts.raw.RawOrganismState convertOrganismState(RawOrganismState apiState) {
        return new org.evochora.server.contracts.raw.RawOrganismState(
                apiState.getOrganismId(),
                apiState.getParentId(),
                apiState.getBirthTick(),
                apiState.getProgramId(),
                apiState.getPosition(), // initialPosition
                apiState.getPosition(), // ip
                apiState.getDv(),
                apiState.getDp(),
                apiState.getActiveDp(),
                apiState.getEnergy(),
                Arrays.stream(apiState.getDataRegisters()).boxed().collect(Collectors.toList()), // drs
                Arrays.stream(apiState.getProcedureRegisters()).boxed().collect(Collectors.toList()), // prs
                Arrays.stream(apiState.getFormalParamRegisters()).boxed().collect(Collectors.toList()), // fprs
                apiState.getLocationRegisters().stream().map(Object.class::cast).collect(Collectors.toList()), // lrs
                apiState.getDataStack().stream().map(DataContractConverter::convertStackValue).collect(Collectors.toCollection(ArrayDeque::new)), // dataStack
                new ArrayDeque<>(apiState.getLocationStack()), // locationStack
                apiState.getCallStack().stream().map(DataContractConverter::convertProcFrame).collect(Collectors.toCollection(ArrayDeque::new)), // callStack
                apiState.isDead(),
                apiState.getErrorState() != null,
                apiState.getErrorState() != null ? apiState.getErrorState().getReason() : null,
                false, // skipIpAdvance
                null, // ipBeforeFetch
                null // dvBeforeFetch
        );
    }

    public static org.evochora.server.contracts.raw.RawCellState convertCellState(RawCellState apiState) {
        return new org.evochora.server.contracts.raw.RawCellState(
                apiState.getPosition(),
                apiState.getValue(),
                apiState.getOwnerId()
        );
    }

    private static Object convertStackValue(StackValue stackValue) {
        if (stackValue.getType() == StackValueType.LITERAL) {
            return stackValue.getLiteralValue();
        } else {
            return stackValue.getPositionValue();
        }
    }

    private static SerializableProcFrame convertProcFrame(org.evochora.datapipeline.api.contracts.SerializableProcFrame apiFrame) {
        return new SerializableProcFrame(
                apiFrame.getProcedureName(),
                apiFrame.getReturnAddress(),
                Arrays.stream(apiFrame.getSavedProcedureRegisters()).boxed().toArray(),
                Arrays.stream(apiFrame.getSavedFormalParamRegisters()).boxed().toArray(),
                null // fprBindings
        );
    }

    private static PlacedMolecule convertPlacedMolecule(SerializablePlacedMolecule apiMolecule) {
        return new PlacedMolecule(apiMolecule.type(), apiMolecule.value());
    }

    private static SourceInfo convertSourceInfo(SerializableSourceInfo apiSourceInfo) {
        return new SourceInfo(apiSourceInfo.sourceName(), apiSourceInfo.line(), apiSourceInfo.column());
    }
}
