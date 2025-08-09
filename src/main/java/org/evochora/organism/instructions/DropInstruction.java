package org.evochora.organism.instructions;

import org.evochora.Simulation;
import org.evochora.assembler.AssemblerOutput;
import org.evochora.organism.Instruction;
import org.evochora.organism.Organism;
import org.evochora.world.World;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class DropInstruction extends Instruction {

    public static final int LENGTH = 1;

    public DropInstruction(Organism organism, int fullOpcodeId) {
        super(organism, fullOpcodeId);
    }

    @Override
    public void execute(Simulation simulation) {
        try {
            organism.getDataStack().pop();
        } catch (NoSuchElementException e) {
            organism.instructionFailed("Stack Underflow: DROP benötigt mindestens 1 Element.");
        }
    }

    public static Instruction plan(Organism organism, World world) {
        int fullOpcodeId = world.getSymbol(organism.getIp()).toInt();
        return new DropInstruction(organism, fullOpcodeId);
    }

    public static AssemblerOutput assemble(String[] args, Map<String, Integer> registerMap, Map<String, Integer> labelMap) {
        if (args.length != 0) throw new IllegalArgumentException("DROP erwartet keine Argumente.");
        return new AssemblerOutput.CodeSequence(List.of());
    }
}
