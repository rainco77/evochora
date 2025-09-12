class DiffCalculator {
    /**
     * Vergleicht zwei Tick-Zustände und gibt ein Objekt mit den Änderungen zurück.
     * @param {object | null} oldTickState - Der alte Tick-Zustand (kann null sein).
     * @param {object} newTickState - Der neue Tick-Zustand.
     * @returns {object} Ein worldChanges-Objekt.
     */
    static calculate(oldTickState, newTickState) {
        const changes = {
            cells: { added: [], removed: [], updated: [] },
            organisms: { added: [], removed: [], updated: [] }
        };

        const oldCells = oldTickState?.worldState?.cells || [];
        const newCells = newTickState?.worldState?.cells || [];

        const oldOrganisms = oldTickState?.worldState?.organisms || [];
        const newOrganisms = newTickState?.worldState?.organisms || [];

        const organismDetails = newTickState?.organismDetails || {};

        // 1. Zellen vergleichen
        const oldCellMap = new Map(oldCells.map(c => [this.getCellKey(c.position), c]));
        const newCellMap = new Map(newCells.map(c => [this.getCellKey(c.position), c]));

        for (const [key, newCell] of newCellMap.entries()) {
            if (oldCellMap.has(key)) {
                const oldCell = oldCellMap.get(key);
                if (this.areCellsDifferent(oldCell, newCell)) {
                    changes.cells.updated.push(newCell);
                }
                oldCellMap.delete(key); // Mark as processed
            } else {
                changes.cells.added.push(newCell);
            }
        }
        changes.cells.removed = Array.from(oldCellMap.values());

        // 2. Organismen vergleichen
        const oldOrganismMap = new Map(oldOrganisms.map(o => [o.id, o]));
        const newOrganismMap = new Map(newOrganisms.map(o => [o.id, o]));

        for (const [id, newOrganism] of newOrganismMap.entries()) {
            // Ergänze organism details für den Vergleich
            const details = organismDetails[id];
            const augmentedNewOrganism = this.augmentOrganism(newOrganism, details);

            if (oldOrganismMap.has(id)) {
                const oldOrganism = this.augmentOrganism(oldOrganismMap.get(id), oldTickState?.organismDetails?.[id]);
                if (this.areOrganismsDifferent(oldOrganism, augmentedNewOrganism)) {
                    changes.organisms.updated.push(augmentedNewOrganism);
                }
                oldOrganismMap.delete(id); // Mark as processed
            } else {
                changes.organisms.added.push(augmentedNewOrganism);
            }
        }
        changes.organisms.removed = Array.from(oldOrganismMap.values()).map(o => this.augmentOrganism(o, oldTickState?.organismDetails?.[o.id]));

        return changes;
    }

    static getCellKey(position) {
        return position.join('|');
    }

    static areCellsDifferent(cellA, cellB) {
        return cellA.type !== cellB.type || cellA.value !== cellB.value || cellA.ownerId !== cellB.ownerId;
    }

    static areOrganismsDifferent(orgA, orgB) {
        // Vergleiche relevante Eigenschaften, die das Rendering beeinflussen
        return this.getCellKey(orgA.position) !== this.getCellKey(orgB.position) ||
               orgA.energy !== orgB.energy ||
               orgA.activeDpIndex !== orgB.activeDpIndex ||
               JSON.stringify(orgA.dps) !== JSON.stringify(orgB.dps) ||
               JSON.stringify(orgA.dv) !== JSON.stringify(orgB.dv);
    }

    /**
     * Reichert ein Organismus-Objekt mit Details an, die für den Vergleich und das Rendering relevant sind.
     */
    static augmentOrganism(organism, details) {
        if (!organism) return null;
        // Kopie erstellen, um das Original nicht zu verändern
        const augmented = { ...organism };

        // Hole den korrekten activeDpIndex aus organismDetails, wenn vorhanden
        const correctActiveDpIndex = details?.internalState?.activeDpIndex ?? organism.activeDpIndex ?? 0;
        augmented.activeDpIndex = correctActiveDpIndex;

        return augmented;
    }
}
