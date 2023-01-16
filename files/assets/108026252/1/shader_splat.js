var ShaderSplat = pc.createScript('shaderSplat');

ShaderSplat.attributes.add('splatShader', {
    type: 'asset',
    assetType: 'shader',
    title: 'Splat Shader'
});

ShaderSplat.attributes.add('circleShader', {
    type: 'asset',
    assetType: 'shader',
    title: 'Circle Shader'
});

ShaderSplat.attributes.add('splatTex', {
    type: 'asset',
    assetType: 'texture',
    title: 'Splat Map'
});
ShaderSplat.attributes.add('redTex', {
    type: 'asset',
    assetType: 'texture',
    title: 'Red Texture'
});
ShaderSplat.attributes.add('greenTex', {
    type: 'asset',
    assetType: 'texture',
    title: 'Green Texture'
});
ShaderSplat.attributes.add('blueTex', {
    type: 'asset',
    assetType: 'texture',
    title: 'Blue Texture'
});
ShaderSplat.attributes.add('locaterTex', {
    type: 'asset',
    assetType: 'texture',
    title: 'Locater Texture'
});
ShaderSplat.attributes.add('spellTex', {
    type: 'asset',
    assetType: 'texture',
    title: 'Spell Texture'
});

// initialize code called once per entity
ShaderSplat.prototype.initialize = function () {

    var app = this.app;
    var render = this.entity.render;
    this.material = render.meshInstances[0].material;

    this.player = app.root.findByName("player");
    var playerPos = this.player.getPosition();

    this.material.chunks.combinePS = this.circleShader.resource;
    this.material.setParameter('locater_texture', this.locaterTex.resource);
    this.material.setParameter('target_position_available', false);
    this.material.setParameter('target_position', [playerPos.x, playerPos.z]);

    this.material.setParameter('selected_char_position', [29.1888, -30.958]);
    this.material.setParameter('selected_char_color', [0, 1, 0]);
    
    this.material.setParameter('spell_position', [playerPos.x, playerPos.z]);
    this.material.setParameter('spell_texture', this.spellTex.resource);

    this.material.chunks.diffusePS = this.splatShader.resource;
    this.material.setParameter('splatMap', this.splatTex.resource);
    this.material.setParameter('texture_red', this.redTex.resource);
    this.material.setParameter('texture_green', this.greenTex.resource);
    this.material.setParameter('texture_blue', this.blueTex.resource);

    this.material.setParameter('scale_factor', app.root.findByName("terrain").script.terrain.height * 2);
    this.material.update();
};

// update code called every frame
ShaderSplat.prototype.update = function (dt) {
};
