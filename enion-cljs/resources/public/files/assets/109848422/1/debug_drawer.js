/**
 * Ammo Debug Drawer 1.0.1
 * by LeXXik, MIT
 * 
 * Draws the current state of the Ammo's dynamics world.
 * 
 * 
 *  How to use:
 * 
 *      const renderer = new AmmoDebugDrawer();
 *      renderer.enabled = true; // false
 * 
 *      API
 * 
 *   -  // Toggle drawing on/off:
 *      renderer.toggle();
 * 
 *   -  // Change the draw mode:
 *      //      0: Disable
 *      //      1: Wireframe
 *      //      2: Bounding Boxes   (default)
 *      //      3: Wireframe + AABB
 *      //      8: Contact Points
 *      renderer.mode = 4;
 * 
 *   -  // Optionally limit the drawing distance around an entity (in meters):
 *      const renderer = new AmmoDebugDrawer({
 *          limit: {
 *              entity: cameraEntity,       // An entity around which to draw
 * 
 *              distance: 30                // Radius around the entity; vertices beyond that distance will be ignored,
 * 
 *              ignorePartials: false       // If true, the lines that are only partially in range will be ignored (default: false)
 *          }
 *      });
 * 
 *   -  // Optionally specify which scene layer to draw to:
 *      const renderer = new AmmoDebugDrawer({ layer: myLayer });   // Should be of type pc.Layer (defaults to UI layer)
 */




class AmmoDebugDrawer {
    
    constructor(opts = {}) {
        if (!window.Ammo) {
            console.warn('Warning! Trying to initialize Ammo Debug Drawer without Ammo lib in the project. Aborting.');
            return;
        }

        const app = pc.Application.getApplication();
        const scene = app.scene;
        const layers = scene.layers;
        const self = this;


        const drawLayer = opts.layer || layers.getLayerByName('Debug Draw') || layers.getLayerById(pc.LAYERID_UI);

        const { entity, distance, ignorePartials } = opts.limit || {};

        const pool = new AmmoDebugDrawer.Pool();

        const v1 = new pc.Vec3();
        const v2 = new pc.Vec3();
        const v3 = new pc.Vec3();

        let debugDrawMode = 1;
        let enabled = false;


        const drawer                = new Ammo.DebugDrawer();
        drawer.drawLine             = drawLine.bind(this);
        drawer.drawContactPoint     = drawContactPoint.bind(this);
        drawer.reportErrorWarning   = reportErrorWarning.bind(this);
        drawer.draw3dText           = draw3dText.bind(this);
        drawer.setDebugMode         = setDebugMode.bind(this);
        drawer.getDebugMode         = getDebugMode.bind(this);
        drawer.enable               = enable.bind(this);
        drawer.disable              = disable.bind(this);
        drawer.update               = update.bind(this);
        
        const world = app.systems.rigidbody.dynamicsWorld;
        world.setDebugDrawer(drawer);


        // ----------------------------------------------------------------


        self.clear                  = clear;
        self.toggle                 = toggle;


        // ----------------------------------------------------------------


        function reportErrorWarning(warningString) {}
        
        function draw3dText(location, textString) {}

        function drawContactPoint(pointOnB, normalOnB, distance, lifeTime, color) {
            const p = Ammo.wrapPointer(pointOnB, Ammo.btVector3);
            const n = Ammo.wrapPointer(normalOnB, Ammo.btVector3);
            const c = Ammo.wrapPointer(color, Ammo.btVector3);
            
            const x = p.x();
            const y = p.y();
            const z = p.z();

            pool.pushPos(x, y, z, x + n.x() * 0.5, y + n.y() * 0.5, z + n.z() * 0.5);
            pool.pushColor(c.x(), c.y(), c.z(), 1, c.x(), c.y(), c.z(), 1);
        }

        function drawLine(from, to, color) {
            const f = Ammo.wrapPointer(from, Ammo.btVector3);
            const t = Ammo.wrapPointer(to, Ammo.btVector3);
            const c = Ammo.wrapPointer(color, Ammo.btVector3);

            if (entity) {
                v1.set(f.x(), f.y(), f.z());
                v2.set(t.x(), t.y(), t.z());

                const pos = entity.getPosition();

                const d1 = pos.distance(v1);
                const d2 = pos.distance(v2);

                if ((d1 < distance && d2 < distance) || (entity && !ignorePartials && (d1 < distance || d2 < distance))) {
                    pool.pushPos(v1.x, v1.y, v1.z, v2.x, v2.y, v2.z);
                    pool.pushColor(c.x(), c.y(), c.z(), 1, c.x(), c.y(), c.z(), 1);
                }
            } else {
                pool.pushPos(f.x(), f.y(), f.z(), t.x(), t.y(), t.z());
                pool.pushColor(c.x(), c.y(), c.z(), 1, c.x(), c.y(), c.z(), 1);
            }
        }

        function clear() {
            pool.clear();
        }

        function setDebugMode(val) {
            debugDrawMode = val;
        }

        function getDebugMode() {
            return debugDrawMode;
        }

        function enable() {
            self.enabled = true;
        }

        function disable() {
            self.enabled = false;
        }

        function toggle() {
            self.enabled = !enabled;
        }

        function draw() {
            try {
                pool.entries.forEach(entry => {
                    app.drawLineArrays(entry.positions, entry.colors, false, drawLayer);
                });
            } catch (e) {
                console.warn('Error drawing debug lines', e);
                disable();
            }
        }

        function update() {
            if (enabled) world.debugDrawWorld();
        }

        function postUpdate() {
            if (enabled) {
                draw();
                clear();
            }
        }

        
        // ----------------------------------------------------------------


        // Getters / Setters
        Object.defineProperties(self, {
            enabled: {
                get: () => { return enabled; },
                set: (val) => {
                    enabled = val;
                    if (enabled) {
                        app.systems.on('update', update, self);
                        app.systems.on('postUpdate', postUpdate, self);
                    } else {
                        app.systems.off('update', update, self);
                        app.systems.off('postUpdate', postUpdate, self);
                        clear();
                    }
                }
            },

            // 0: Disable
            // 1: Wireframe
            // 2: Bounding Boxes
            // 3: Wireframe + AABB
            // 8: Contact Points
            mode: {
                get: () => { return debugDrawMode; },
                set: (val) => { debugDrawMode = val; }
            }
        });
    }    
}

AmmoDebugDrawer.Pool = class Pool {
    constructor() {
        const self          = this;

        const pool          = new Map();
        const MAX_SIZE      = 64000;

        let index           = 0;

        pool.set(index, { 'positions' : [], 'colors': [] });


        self.entries        = pool;
        self.clear          = clear;
        self.pushColor      = pushColor;
        self.pushPos        = pushPos;


        _add(index);


        function clear() {
            pool.clear();
            index = 0;
            _add(index);
        }

        function pushColor(r1, g1, b1, a1, r2, g2, b2, a2) {
            const entry = _getEntry('colors', 8);
            entry.colors.push(r1, g1, b1, a1, r2, g2, b2, a2);
        }

        function pushPos(x1, y1, z1, x2, y2, z2) {
            const entry = _getEntry('positions', 6);
            entry.positions.push(x1, y1, z1, x2, y2, z2);
        }

        function _add(index) {
            const entry = { positions: [], colors: [] };
            pool.set(index, entry);
            return entry;
        }

        function _getEntry(buffer, increment) {
            let entry = pool.get(index);
            if (entry[buffer].length + increment > MAX_SIZE) {
                entry = _add(++index);
            }
            return entry;
        }        
    }
};
