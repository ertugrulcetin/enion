
uniform sampler2D locater_texture;
uniform vec2 target_position;
uniform bool target_position_available;

uniform bool selected_char_position_available;
uniform vec2 selected_char_position;
uniform vec3 selected_char_color;

uniform vec2 spell_position;

const int terrain_size = 100;

uniform sampler2D spell_texture;
const int spell_texture_scale = 25;
const float spell_range = 2.;
vec2 spell_uv_offset_vec = vec2(0.48);

const int locater_texture_scale = 400;
const float locater_range = 0.1;
vec2 locater_uv_offset_vec = vec2(0.4987);

vec2 size = vec2(terrain_size);

const float char_selection_range = 0.25;

float getShape(vec2 position, float r){
    float dist = distance(position, vPositionW.xz);
    return (smoothstep(r-0.1, r, dist) * 0.75 + 0.25) - smoothstep(r, r + 0.1, dist);
}

vec3 getTargetPosition(){
 if(target_position_available){

    float shape = getShape(target_position, locater_range);

    vec2 uvpoint = target_position / size;
    vec4 texture = texture2DSRGB(locater_texture, (uv_cord - uvpoint - locater_uv_offset_vec) * vec2(locater_texture_scale));

    //vec3 tt =mix(dAlbedo, vec3(1, 0, 0), shape * 0.15);
    vec3 tt = mix(dAlbedo, texture.rgb, shape * texture.a * 0.2);
    return tt;
 }
    return dAlbedo;
}

vec3 getSelectedCharPosition(vec3 tt){
    if(selected_char_position_available){
        float shape = getShape(selected_char_position, char_selection_range);
        return mix(tt, selected_char_color, shape * 0.15);
    }
    return tt;
}

vec3 combineColor() {
   vec3 tt = getTargetPosition();
   tt = getSelectedCharPosition(tt);

   return tt * dDiffuseLight;
}
