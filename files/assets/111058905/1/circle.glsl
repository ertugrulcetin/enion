
// I'm using both chunks.diffusePS and chunks.combinePS at the same time
uniform vec2 character_position;
uniform float dt;


vec2 rotateUV(vec2 uv, vec2 pivot, float rotation) {
    float sine = sin(rotation);
    float cosine = cos(rotation);

    uv -= pivot;
    uv.x = uv.x * cosine - uv.y * sine;
    uv.y = uv.x * sine + uv.y * cosine;
    uv += pivot;

    return uv;
}

vec2 rotateUV(vec2 uv, float rotation) {
    float mid = 0.5;
    float cosine = cos(rotation);
    float sine = sin(rotation);
    return vec2(
        cosine * (uv.x - mid) + sine * (uv.y - mid) + mid,
        cosine * (uv.y - mid) - sine * (uv.x - mid) + mid
    );
}

 // scale = vec2(map_height, map_widht); 100x100
// my_cord = $UV;

float roat = 0.0;
vec3 combineColor() {

    // vec2 scale = vec2(100,100);
    float dist = distance(character_position, vPositionW.xz);
    float r = 1.0;
    float shape = (smoothstep(r-0.1, r, dist)*0.75 + 0.25) - smoothstep(r, r + 0.1, dist);

    float draw =pow(saturate(1. - dist), 5.);
    // float draw = saturate(1. - dist);

    vec4 texture = texture2DSRGB(texture_splat , my_cord * scale);
    
    // float draw =pow(saturate(1-distance(i.worldPos,_Coordinate.xyz)),100);
    // return  texture.rgb + dAlbedo;

    vec3 tt = saturate(mix(dAlbedo, texture.rgb,  texture.a / 0.4 * draw));
    return tt * dDiffuseLight;
}
