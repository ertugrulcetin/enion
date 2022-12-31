
uniform sampler2D locater_texture;
uniform vec2 target_position;
uniform bool target_position_available;

const int terrain_size = 100;
const int locater_texture_scale = 400;

const float r = 0.1;
vec2 size = vec2(terrain_size);

float getShape(vec2 position, float r){
    float dist = distance(position, vPositionW.xz);
    return (smoothstep(r-0.1, r, dist)*0.75 + 0.25) - smoothstep(r, r + 0.1, dist);
}

vec3 combineColor() {
 if(target_position_available){

    float shape = getShape(target_position, r);

    vec2 uvpoint = target_position / size;
    vec4 texture = texture2DSRGB(locater_texture, (my_cord - uvpoint - vec2(0.4987)) * vec2(locater_texture_scale));

    vec3 tt = mix(dAlbedo, texture.rgb, shape * texture.a * 0.2);
    return tt * dDiffuseLight;
    }
    
    return dAlbedo * dDiffuseLight;
}
