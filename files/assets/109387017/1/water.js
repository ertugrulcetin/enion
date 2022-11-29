var Water = pc.createScript('water');

Water.attributes.add('vs', {
    type: 'asset',
    assetType: 'shader',
    title: 'Vertex Shader'
});

Water.attributes.add('fs', {
    type: 'asset',
    assetType: 'shader',
    title: 'Fragment Shader'
});

Water.attributes.add('surfaceTexture', {
    type: 'asset',
    assetType: 'texture',
    title: 'Surface Texture'
});

Water.attributes.add('isMask', {type:'boolean',title:"Is Mask?"});

Water.attributes.add('subdivisions', { type : 'number', default : 100 });
Water.attributes.add('width', { type : 'number', default : 1000 });
Water.attributes.add('height', { type : 'number', default : 1000 });

Water.prototype.GeneratePlaneMesh = function(options){
    // 1 - Set default options if none are provided 
    var factor = 50;
    if(options === undefined)
        options = {subdivisions:this.subdivisions , width:this.width, height:this.height};
    // 2 - Generate points, uv's and indices 

    var positions = [];
    var uvs = [];
    var indices = [];
    var row, col;
    var normals;

    for (row = 0; row <= options.subdivisions; row++) {
        for (col = 0; col <= options.subdivisions; col++) {
            var position = new pc.Vec3((col * options.width) / options.subdivisions - (options.width / 2.0), 0, ((options.subdivisions - row) * options.height) / options.subdivisions - (options.height / 2.0));
            
            positions.push(position.x, position.y, position.z);
            
            uvs.push(col / options.subdivisions, 1.0 - row / options.subdivisions);
        }
    }

    for (row = 0; row < options.subdivisions; row++) {
        for (col = 0; col < options.subdivisions; col++) {
            indices.push(col + row * (options.subdivisions + 1));
            indices.push(col + 1 + row * (options.subdivisions + 1));
            indices.push(col + 1 + (row + 1) * (options.subdivisions + 1));

            indices.push(col + row * (options.subdivisions + 1));
            indices.push(col + 1 + (row + 1) * (options.subdivisions + 1));
            indices.push(col + (row + 1) * (options.subdivisions + 1));
        }
    }
    
    // Compute the normals 
    normals = pc.calculateNormals(positions, indices);

    
    // Make the actual model
    var node = new pc.GraphNode();
    var material = this.CreateWaterMaterial();
   
    // Create the mesh 
    var mesh = pc.createMesh(this.app.graphicsDevice, positions, {
        normals: normals,
        uvs: uvs,
        indices: indices
    });

    var meshInstance = new pc.MeshInstance(node, mesh, material);
    
    // Add it to this entity 
    var model = new pc.Model();
    model.graph = node;
    model.meshInstances.push(meshInstance);
    
    this.entity.addComponent('model');
    this.entity.model.model = model;
    this.entity.model.castShadows = false; // We don't want the water surface itself to cast a shadow 
    
    // Set the culling masks 
    var bit = this.isMask ? 3 : 2; 
    meshInstance.mask = 0; 
    meshInstance.mask |= (1 << bit);
};

Water.prototype.CreateWaterMaterial = function(){
    // Create a new blank material  
    var material = new pc.Material();
    // A name just makes it easier to identify when debugging 
    material.name = "DynamicWater_Material";
    
    // Create the shader definition 
    // dynamically set the precision depending on device.
    var gd = this.app.graphicsDevice;
    var fragmentShader = "precision " + gd.precision + " float;\n";
    fragmentShader = fragmentShader + this.fs.resource;
    
    var vertexShader = this.vs.resource;

    // A shader definition used to create a new shader.
    var shaderDefinition = {
        attributes: {           
            aPosition: pc.gfx.SEMANTIC_POSITION,
            aUv0: pc.SEMANTIC_TEXCOORD0,
        },
        vshader: vertexShader,
        fshader: fragmentShader
    };
    
    // Create the shader from the definition
    this.shader = new pc.Shader(gd, shaderDefinition);
    
    // Set blending 
    material.blendType = pc.BLEND_NORMAL;
    //material.alphaToCoverage = true;
    
    // Define our uniforms
    if(!this.camera){
        this.camera = this.app.root.findByName("camera").camera;
    }
    var camera = this.camera; 
    var n = camera.nearClip;
    var f = camera.farClip;
    var camera_params = [
        1/f,
        f,
        (1-f / n) / 2,
        (1 + f / n) / 2
    ];
            
    material.setParameter('camera_params', camera_params);
    material.setParameter('uTime',this.time);
    material.setParameter('uSurfaceTexture',this.surfaceTexture.resource);
    material.setParameter('isMask',this.isMask);
    this.material = material; // Save a reference to this material
    
    // Apply shader to this material 
    material.setShader(this.shader);
    
    return material;
};

// initialize code called once per entity
Water.prototype.initialize = function() {
    this.time = 0;
    
    this.GeneratePlaneMesh();
    
    // Save the current shaders 
    this.savedVS = this.vs.resource;
    this.savedFS = this.fs.resource;
};

// update code called every frame
Water.prototype.update = function(dt) {
    
    if(this.savedFS != this.fs.resource || this.savedVS != this.vs.resource){
        // Re-create the material so the shaders can be recompiled 
        var newMaterial = this.CreateWaterMaterial();
        // Apply it to the model 
        var model = this.entity.model.model;
        var mesh = model.meshInstances[0]; 
        mesh.material = newMaterial;  
        
        // Save the new shaders
        this.savedVS = this.vs.resource;
        this.savedFS = this.fs.resource;
    }
    
    this.time += 0.05; 
    this.material.setParameter('uTime',this.time);
};

// swap method called for script hot-reloading
// inherit your script state here
Water.prototype.swap = function(old) { 
    this.time = old.time;
};

// to learn more about script anatomy, please read:
// http://developer.playcanvas.com/en/user-manual/scripting/