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
            backgroundColor: 0x0a0a14, // Use hex for Pixi
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

        this.renderer = new WorldRenderer(this.canvas, defaultConfig, {});
        this.sidebar = new SidebarView(document.getElementById('sidebar'), this);
        this.sidebarManager = new SidebarManager(this);
        this.toolbar = new ToolbarView(this);
        this.state = { currentTick: 0, selectedOrganismId: null, lastTickData: null, totalTicks: null };
        this.canvas.addEventListener('click', (e) => this.onCanvasClick(e));
    }

    async init() {
        // Initialize the new renderer
        await this.renderer.init();

        // Load URL parameters first
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
                // First load, draw everything
                this.renderer.drawInitial(newData);
            } else {
                // Subsequent loads, calculate diff and apply changes
                const changes = DiffCalculator.calculate(oldData, newData);
                this.renderer.applyChanges(changes, newData);
            }

            // Update the state for the next navigation
            this.state.lastTickData = newData;

            // Update UI elements with the new data
            const ids = Object.keys(newData.organismDetails || {});
            const sel = this.state.selectedOrganismId && ids.includes(this.state.selectedOrganismId) ? this.state.selectedOrganismId : null;
            if (sel) {
                this.sidebar.update(newData.organismDetails[sel], 'goto');
                this.sidebarManager.autoShow();
                this.sidebarManager.setToggleButtonVisible(true);
            } else {
                this.sidebarManager.autoHide();
                this.sidebarManager.setToggleButtonVisible(false);
            }
            this.updateTickUi();
            this.saveToUrl();

        } catch (error) {
            console.error('Failed to navigate to tick:', error);
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

    onCanvasClick(event) {
        // This needs to be adapted for PixiJS's coordinate system and event handling
        // For now, we keep it simple or disable it if it causes issues.
        // The new renderer doesn't implement picking yet.
        console.log("Canvas click - picking not implemented in WebGL renderer yet.");
    }

    showError(message) {
        if (window.EvoDebugger && window.EvoDebugger.statusManager) {
            window.EvoDebugger.statusManager.showError(message);
        } else {
            console.error('Error:', message);
            alert('Fehler: ' + message);
        }
    }

    resetKeyboardEvents() {
        if (this.toolbar && this.toolbar.handleKeyRelease) {
            this.toolbar.handleKeyRelease();
        }
    }

    loadFromUrl() {
        const urlParams = new URLSearchParams(window.location.search);
        const tick = urlParams.get('tick');
        const organism = urlParams.get('organism');

        if (tick !== null) {
            const tickNumber = parseInt(tick, 10);
            if (!isNaN(tickNumber) && tickNumber >= 0) {
                this.state.currentTick = tickNumber;
            }
        }

        if (organism !== null) {
            this.state.selectedOrganismId = organism;
        }
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
