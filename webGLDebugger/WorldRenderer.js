class WorldRenderer {
    static organismColorPalette = [
        0x32cd32, 0x1e90ff, 0xdc143c, 0xffd700,
        0xffa500, 0x9370db, 0x00ffff
    ];

    constructor(canvas, config, appController) {
        this.canvas = canvas;
        this.config = config;
        this.appController = appController;
        this.organismColorMap = new Map();
        this.selectedOrganismId = null;

        this.cellGraphics = new Map();
        this.organismGraphics = new Map();
    }

    async init() {
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

        this.backgroundLayer = new PIXI.Container();
        this.foregroundLayer = new PIXI.Container();
        this.app.stage.addChild(this.backgroundLayer, this.foregroundLayer);
    }

    updateWorldShape(worldShape) {
        if (worldShape && Array.isArray(worldShape) && worldShape.length >= 2) {
            this.config.worldSize = worldShape;
            const [width, height] = this.config.worldSize;
            this.app.renderer.resize(width * this.config.cellSize, height * this.config.cellSize);
        }
    }

    selectOrganism(organismId) {
        this.selectedOrganismId = organismId;
        this.organismGraphics.forEach((graphics) => {
            this._drawOrganism(graphics.organism, graphics);
        });
    }

    drawInitial(worldState) {
        if (!this.app) { console.error("Pixi app not initialized!"); return; }

        const { cells, organisms } = this.processWorldState(worldState);

        this.backgroundLayer.removeChildren();
        this.foregroundLayer.removeChildren();
        this.cellGraphics.clear();
        this.organismGraphics.clear();

        for (const cell of cells) { this._addCell(cell); }
        for (const organism of organisms) { this._addOrganism(organism); }
    }

    applyChanges(worldChanges, newWorldState) {
        if (!this.app) return;

        const { cells: cellChanges, organisms: organismChanges } = worldChanges;

        cellChanges.removed.forEach(cell => this._removeCell(cell));
        cellChanges.added.forEach(cell => this._addCell(cell));
        cellChanges.updated.forEach(cell => this._updateCell(cell));

        organismChanges.removed.forEach(organism => this._removeOrganism(organism));
        organismChanges.added.forEach(organism => this._addOrganism(organism));
        organismChanges.updated.forEach(organism => this._updateOrganism(organism));
    }

    // --- Cell Rendering ---
    _addCell(cell) {
        const key = this._getCellKey(cell.position);
        const container = new PIXI.Container();
        const bg = new PIXI.Graphics();
        const text = new PIXI.Text({style: {
            fontFamily: "'Monospaced', 'Courier New'",
            fontSize: this.config.cellSize * 0.4,
            fill: this._getTextColorForType(cell.type),
            align: 'center',
        }});
        text.anchor.set(0.5);
        container.addChild(bg, text);
        const graphics = { container, bg, text };
        this._drawCell(graphics, cell);
        this.backgroundLayer.addChild(container);
        this.cellGraphics.set(key, graphics);
    }

    _updateCell(cell) {
        const graphics = this.cellGraphics.get(this._getCellKey(cell.position));
        if (graphics) this._drawCell(graphics, cell);
        else this._addCell(cell);
    }

    _removeCell(cell) {
        const key = this._getCellKey(cell.position);
        const graphics = this.cellGraphics.get(key);
        if (graphics) {
            this.backgroundLayer.removeChild(graphics.container);
            graphics.container.destroy({ children: true });
            this.cellGraphics.delete(key);
        }
    }

    _drawCell(graphics, cell) {
        const { container, bg, text } = graphics;
        container.position.set(cell.position[0] * this.config.cellSize, cell.position[1] * this.config.cellSize);

        bg.clear();
        bg.fill(this._getBackgroundColorForType(cell.type));
        bg.drawRect(0, 0, this.config.cellSize, this.config.cellSize); // FIX: Use drawRect

        let cellText = '';
        if ((cell.type === this.config.typeCode && (cell.value !== 0 || cell.ownerId !== 0)) || cell.type !== this.config.typeCode) {
            cellText = (cell.type === this.config.typeCode) ? (cell.opcodeName || String(cell.value)) : String(cell.value);
        }
        text.text = cellText;
        text.style.fill = this._getTextColorForType(cell.type);
        text.position.set(this.config.cellSize / 2, this.config.cellSize / 2);
    }

    // --- Organism Rendering ---
    _addOrganism(organism) {
        if (!organism || !Array.isArray(organism.position)) return;

        const container = new PIXI.Container();
        const ip = new PIXI.Graphics();
        ip.interactive = true;
        ip.cursor = 'pointer';
        ip.on('pointerdown', () => this.appController.handleOrganismSelection(organism.id));

        const dv = new PIXI.Graphics();
        const dpsContainer = new PIXI.Container();
        container.addChild(ip, dv, dpsContainer);

        const graphics = { organism, container, ip, dv, dpsContainer, dps: new Map() };
        this._drawOrganism(organism, graphics);

        this.foregroundLayer.addChild(container);
        this.organismGraphics.set(organism.id, graphics);
    }

    _updateOrganism(organism) {
        if (!organism || !Array.isArray(organism.position)) return;
        const graphics = this.organismGraphics.get(organism.id);
        if (graphics) {
            graphics.organism = organism;
            this._drawOrganism(organism, graphics);
        }
        else this._addOrganism(organism);
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
        const { container, ip, dv, dpsContainer } = graphics;
        const pos = organism.position;
        if (!pos) return;

        const color = this._getOrganismColor(organism.id);
        const isSelected = this.selectedOrganismId === String(organism.id);

        container.position.set(pos[0] * this.config.cellSize, pos[1] * this.config.cellSize);

        ip.clear();
        ip.stroke({ width: isSelected ? 4 : 2.5, color: organism.energy <= 0 ? this.config.colorDead : color });
        ip.fill({ alpha: 0.001 }); // FIX: Use transparent fill for hit area
        ip.drawRect(0, 0, this.config.cellSize, this.config.cellSize);

        this._drawDv(dv, organism.dv, color);

        dpsContainer.removeChildren();
        const newDps = new Map();
        if (Array.isArray(organism.dps)) {
            organism.dps.forEach((dpPos, index) => {
                const key = this._getCellKey(dpPos);
                if (!newDps.has(key)) newDps.set(key, { pos: dpPos, indices: [] });
                newDps.get(key).indices.push(index);
            });
        }

        for(const dpData of newDps.values()) {
            const isActive = dpData.indices.includes(organism.activeDpIndex);
            const dpGraphics = this._drawDp(dpData.pos, color, dpData.indices, isActive, pos);
            dpsContainer.addChild(dpGraphics);
        }
    }

    _drawDv(g, dv, color) {
        g.clear();
        if (!Array.isArray(dv)) return;
        const edgeOffset = this.config.cellSize * 0.5, cx = this.config.cellSize / 2, cy = this.config.cellSize / 2;
        const px = cx + Math.sign(dv[0] || 0) * edgeOffset * 0.9;
        const py = cy + Math.sign(dv[1] || 0) * edgeOffset * 0.9;
        g.circle(px, py, this.config.cellSize * 0.1).fill(color);
    }

    _drawDp(pos, color, indices, isActive, ipPos) {
        const container = new PIXI.Container();
        container.position.set((pos[0] - ipPos[0]) * this.config.cellSize, (pos[1] - ipPos[1]) * this.config.cellSize);

        const border = new PIXI.Graphics();
        this._drawDashedRect(border, 0, 0, this.config.cellSize, this.config.cellSize, 2.0, color);
        container.addChild(border);

        if (isActive) {
            const hatching = new PIXI.Graphics().stroke({width: 1.0, color: color});
            const spacing = 4;
            for (let i = -this.config.cellSize; i < this.config.cellSize; i += spacing) {
                hatching.moveTo(Math.max(0, i), Math.max(0, -i)).lineTo(Math.min(this.config.cellSize, i + this.config.cellSize), Math.min(this.config.cellSize, this.config.cellSize - i));
            }
            container.addChild(hatching);
        }
        if (indices.length > 0) {
            const text = new PIXI.Text({text: indices.join(','), style: {
                fontFamily: "'Monospaced', 'Courier New'", fontSize: this.config.cellSize * 0.45,
                fill: color, stroke: { color: 'black', width: 2, join: 'round' }, fontWeight: '900'
            }});
            text.anchor.set(0.5);
            text.position.set(this.config.cellSize / 2, this.config.cellSize / 2);
            container.addChild(text);
        }
        return container;
    }

    _drawDashedRect(g, x, y, width, height, lineWidth, color) {
        g.stroke({ width: lineWidth, color: color });
        const dash = 3;
        const gap = 3;
        for(let i = x; i < x + width; i += dash + gap) g.moveTo(i, y).lineTo(i + dash, y);
        for(let i = y; i < y + height; i += dash + gap) g.moveTo(x + width, i).lineTo(x + width, i + dash);
        for(let i = x + width; i > x; i -= dash + gap) g.moveTo(i, y + height).lineTo(i - dash, y + height);
        for(let i = y + height; i > y; i -= dash + gap) g.moveTo(x, i).lineTo(x, i - dash);
    }

    processWorldState(worldState) {
        const typeToId = t => ({ CODE: 0, DATA: 1, ENERGY: 2, STRUCTURE: 3 })[t] ?? 1;
        const cells = (worldState.worldState?.cells || []).map(c => ({ ...c, type: typeToId(c.type) }));
        const organisms = (worldState.worldState?.organisms || []).map(o => {
            const details = worldState.organismDetails?.[o.id];
            return { ...o, activeDpIndex: details?.internalState?.activeDpIndex ?? o.activeDpIndex ?? 0 };
        });
        return { cells, organisms };
    }

    _getCellKey(position) { return position.join('|'); }
    _getOrganismColor(id) {
        if (!this.organismColorMap.has(id)) {
            this.organismColorMap.set(id, WorldRenderer.organismColorPalette[(id - 1) % WorldRenderer.organismColorPalette.length]);
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
    _getTextColorForType(typeId) {
        const C = this.config;
         switch (typeId) {
            case C.typeStructure: return C.colorStructureText;
            case C.typeEnergy: return C.colorEnergyText;
            case C.typeData: return C.colorDataText;
            case C.typeCode: return C.colorCodeText;
            default: return C.colorCodeText;
        }
    }
}
