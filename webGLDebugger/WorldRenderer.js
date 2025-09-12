class WorldRenderer {
    static organismColorPalette = [
        0x32cd32, 0x1e90ff, 0xdc143c, 0xffd700,
        0xffa500, 0x9370db, 0x00ffff
    ];

    constructor(canvas, config, isa) {
        this.canvas = canvas;
        this.config = config;
        this.isa = isa; // ISA is not used, but kept for compatibility
        this.organismColorMap = new Map();

        // Internal maps to store references to PIXI objects
        this.cellGraphics = new Map(); // key: "x|y", value: PIXI.Graphics
        this.organismGraphics = new Map(); // key: organismId, value: { container: PIXI.Container, ip: PIXI.Graphics, dps: Map, dv: PIXI.Graphics }

        // Tooltip
        this.tooltip = document.getElementById('cell-tooltip');
        this.setupTooltipEvents();
    }

    async init() {
        // PIXI App
        this.app = new PIXI.Application();
        await this.app.init({
            canvas: this.canvas,
            width: this.canvas.width,
            height: this.canvas.height,
            backgroundColor: this.config.backgroundColor,
            antialias: true,
            resolution: window.devicePixelRatio || 1,
            autoDensity: true
        });

        // Layers
        this.backgroundLayer = new PIXI.Container(); // For cells
        this.foregroundLayer = new PIXI.Container(); // For organisms, markers
        this.app.stage.addChild(this.backgroundLayer, this.foregroundLayer);
    }

    updateWorldShape(worldShape) {
        if (worldShape && Array.isArray(worldShape) && worldShape.length >= 2) {
            this.config.worldSize = worldShape;
            const [width, height] = this.config.worldSize;
            this.app.renderer.resize(width * this.config.cellSize, height * this.config.cellSize);
        }
    }

    /**
     * Draws the initial state of the world.
     * @param {object} worldState - The full world state for the first tick.
     */
    drawInitial(worldState) {
        if (!this.app) {
            console.error("Pixi app not initialized!");
            return;
        }

        const { cells, organisms } = this.processWorldState(worldState);

        // Clear everything
        this.backgroundLayer.removeChildren();
        this.foregroundLayer.removeChildren();
        this.cellGraphics.clear();
        this.organismGraphics.clear();

        // Draw all cells
        for (const cell of cells) {
            this._addCell(cell);
        }

        // Draw all organisms
        for (const organism of organisms) {
            this._addOrganism(organism);
        }
    }

    /**
     * Applies incremental changes to the world.
     * @param {object} worldChanges - The diff object from DiffCalculator.
     * @param {object} newWorldState - The complete new world state.
     */
    applyChanges(worldChanges, newWorldState) {
        if (!this.app) return;

        const { cells: cellChanges, organisms: organismChanges } = worldChanges;
        const { organisms: newOrganisms } = this.processWorldState(newWorldState);

        // Process cell changes
        cellChanges.removed.forEach(cell => this._removeCell(cell));
        cellChanges.added.forEach(cell => this._addCell(cell));
        cellChanges.updated.forEach(cell => this._updateCell(cell));

        // Process organism changes
        organismChanges.removed.forEach(organism => this._removeOrganism(organism));
        // For added and updated, we use the full new organism list to handle selections correctly
        organismChanges.added.forEach(organism => this._addOrganism(organism));
        organismChanges.updated.forEach(organism => this._updateOrganism(organism));
    }

    _addCell(cell) {
        const key = this._getCellKey(cell.position);
        const g = new PIXI.Graphics();
        this._drawCell(g, cell);
        this.backgroundLayer.addChild(g);
        this.cellGraphics.set(key, g);
    }

    _updateCell(cell) {
        const key = this._getCellKey(cell.position);
        const g = this.cellGraphics.get(key);
        if (g) {
            this._drawCell(g, cell);
        } else {
            // If it doesn't exist for some reason, add it
            this._addCell(cell);
        }
    }

    _removeCell(cell) {
        const key = this._getCellKey(cell.position);
        const g = this.cellGraphics.get(key);
        if (g) {
            this.backgroundLayer.removeChild(g);
            g.destroy();
            this.cellGraphics.delete(key);
        }
    }

    _drawCell(g, cell) {
        g.clear();
        const pos = cell.position;
        const x = pos[0] * this.config.cellSize;
        const y = pos[1] * this.config.cellSize;

        // Background
        g.rect(x, y, this.config.cellSize, this.config.cellSize).fill(this._getBackgroundColorForType(cell.type));
    }

    _addOrganism(organism) {
        const container = new PIXI.Container();
        const ip = new PIXI.Graphics();
        container.addChild(ip);

        const graphics = { container, ip, dps: new Map(), dv: new PIXI.Graphics() };
        container.addChild(graphics.dv);

        this._drawOrganism(organism, graphics);

        this.foregroundLayer.addChild(container);
        this.organismGraphics.set(organism.id, graphics);
    }

    _updateOrganism(organism) {
        const graphics = this.organismGraphics.get(organism.id);
        if (graphics) {
            this._drawOrganism(organism, graphics);
        } else {
            this._addOrganism(organism);
        }
    }

    _removeOrganism(organism) {
        const graphics = this.organismGraphics.get(organism.id);
        if (graphics) {
            this.foregroundLayer.removeChild(graphics.container);
            graphics.container.destroy({ children: true });
            this.organismGraphics.delete(organism.id);
        }
    }

    _drawOrganism(organism, graphics) {
        const { container, ip } = graphics;
        const pos = organism.position;
        container.position.set(pos[0] * this.config.cellSize, pos[1] * this.config.cellSize);

        const color = this._getOrganismColor(organism.id);

        // Draw IP
        ip.clear();
        ip.stroke({ width: 2.5, color: organism.energy <= 0 ? this.config.colorDead : color });
        ip.drawRect(0, 0, this.config.cellSize, this.config.cellSize);

        // Simplified DP and DV drawing for now
    }

    // Helper methods
    processWorldState(worldState) {
        const typeToId = t => ({ CODE: 0, DATA: 1, ENERGY: 2, STRUCTURE: 3 })[t] ?? 1;
        const cells = (worldState.worldState?.cells || []).map(c => ({ ...c, type: typeToId(c.type) }));
        const organisms = (worldState.worldState?.organisms || []).map(o => {
            const details = worldState.organismDetails?.[o.id];
            const correctActiveDpIndex = details?.internalState?.activeDpIndex ?? o.activeDpIndex ?? 0;
            return { ...o, activeDpIndex: correctActiveDpIndex };
        });
        return { cells, organisms };
    }

    _getCellKey(position) {
        return position.join('|');
    }

    _getOrganismColor(id) {
        if (!this.organismColorMap.has(id)) {
            const paletteIndex = (id - 1) % WorldRenderer.organismColorPalette.length;
            this.organismColorMap.set(id, WorldRenderer.organismColorPalette[paletteIndex]);
        }
        return this.organismColorMap.get(id);
    }

    _getBackgroundColorForType(typeId) {
        const C = this.config;
        switch (typeId) {
            case C.typeCode: return C.colorCodeBg;
            case C.typeData: return C.colorDataBg;
            case C.typeEnergy: return C.colorEnergyBg;
            case C.typeStructure: return C.colorStructureBg;
            default: return C.colorEmptyBg;
        }
    }

    // Dummy tooltip methods for now
    setupTooltipEvents() {}
    handleMouseMove(event) {}
    showTooltip(event, cell, gridX, gridY) {}
    hideTooltip() {}
}
