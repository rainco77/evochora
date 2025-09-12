class AppController {
    constructor() {
        this.statusManager = new StatusManager();
        this.api = new ApiService(this.statusManager);
        this.canvas = document.getElementById('worldCanvas');

        const defaultConfig = {
            worldSize: [100,30],
            cellSize: 22,
            typeCode: 0,
            typeData: 1,
            typeEnergy: 2,
            typeStructure: 3,
            backgroundColor: 0x0a0a14,
            colorEmptyBg: 0x14141e,
            colorCodeBg: 0x3c5078,
            colorDataBg: 0x32323c,
            colorStructureBg: 0xff7878,
            colorEnergyBg: 0xffe664,
            colorCodeText: '#ffffff',
            colorDataText: '#ffffff',
            colorStructureText: '#323232',
            colorEnergyText: '#323232',
            colorDead: 0x505050
        };

        // Pass `this` to the renderer for callbacks
        this.renderer = new WorldRenderer(this.canvas, defaultConfig, this);
        this.sidebar = new SidebarView(document.getElementById('sidebar'), this);
        this.sidebarManager = new SidebarManager(this);
        this.toolbar = new ToolbarView(this);
        this.state = { currentTick: 0, selectedOrganismId: null, lastTickData: null, totalTicks: null };
    }

    async init() {
        await this.renderer.init();
        this.loadFromUrl();
        await this.navigateToTick(this.state.currentTick);
    }

    async navigateToTick(tick) {
        let target = typeof tick === 'number' ? tick : 0;
        if (target < 0) target = 0;

        try {
            const newData = await this.api.fetchTickData(target);
            const oldData = this.state.lastTickData;

            this.state.currentTick = target;

            if (typeof newData.totalTicks === 'number') {
                this.state.totalTicks = newData.totalTicks;
            }
            if (newData.worldMeta && Array.isArray(newData.worldMeta.shape)) {
                this.renderer.updateWorldShape(newData.worldMeta.shape);
            }

            if (!oldData) {
                this.renderer.drawInitial(newData);
            } else {
                const changes = DiffCalculator.calculate(oldData, newData);
                this.renderer.applyChanges(changes, newData);
            }

            this.state.lastTickData = newData;

            this.updateSidebar();
            this.updateTickUi();
            this.saveToUrl();

        } catch (error) {
            console.error('Failed to navigate to tick:', error);
        }
    }

    handleOrganismSelection(organismId) {
        this.state.selectedOrganismId = String(organismId);
        this.renderer.selectOrganism(this.state.selectedOrganismId); // Inform renderer
        this.updateSidebar();
        this.saveToUrl();
    }

    updateSidebar() {
        const data = this.state.lastTickData;
        if (!data) return;

        const ids = Object.keys(data.organismDetails || {});
        const selId = this.state.selectedOrganismId;

        if (selId && ids.includes(selId)) {
            this.sidebar.update(data.organismDetails[selId], 'goto');
            this.sidebarManager.autoShow();
            this.sidebarManager.setToggleButtonVisible(true);
        } else {
            this.sidebarManager.autoHide();
            this.sidebarManager.setToggleButtonVisible(false);
        }
    }

    updateTickUi() {
        const input = document.getElementById('tick-input');
        if (input) input.value = String(this.state.currentTick || 0);
        const suffix = document.getElementById('tick-total-suffix');
        if (suffix) suffix.textContent = '/' + (this.state.totalTicks != null ? this.state.totalTicks : 'N/A');
        if (input && typeof this.state.totalTicks === 'number' && this.state.totalTicks > 0) {
            try { input.max = String(Math.max(0, this.state.totalTicks - 1)); } catch (_) {}
        }
    }

    loadFromUrl() {
        const urlParams = new URLSearchParams(window.location.search);
        const tick = urlParams.get('tick');
        const organism = urlParams.get('organism');

        if (tick !== null) {
            const tickNumber = parseInt(tick, 10);
            if (!isNaN(tickNumber) && tickNumber >= 0) this.state.currentTick = tickNumber;
        }

        if (organism !== null) this.state.selectedOrganismId = organism;
    }

    saveToUrl() {
        const url = new URL(window.location);
        const params = url.searchParams;

        if (this.state.currentTick !== null && this.state.currentTick !== 0) {
            params.set('tick', this.state.currentTick.toString());
        } else {
            params.delete('tick');
        }

        if (this.state.selectedOrganismId !== null) {
            params.set('organism', this.state.selectedOrganismId);
        } else {
            params.delete('organism');
        }

        window.history.replaceState({}, '', url.toString());
    }
}
