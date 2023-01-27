uniform sampler2D splatMap;
uniform sampler2D texture_red;
uniform sampler2D texture_green;
uniform sampler2D texture_blue;
uniform sampler2D texture_alpha;
uniform int scale_factor;

vec2 uv_cord;
vec2 scale;

void getAlbedo() {
   
    scale = vec2(scale_factor, scale_factor);
    uv_cord = $UV;
    
    vec4 texture0 = texture2DSRGB( texture_red, $UV * scale);
    vec4 texture1 = texture2DSRGB( texture_green, $UV * scale);
    vec4 texture2 = texture2DSRGB( texture_blue, $UV * scale);

    // vec4 texture3 = texture2DSRGB( texture_splat, $UV * scale);
    vec4 splat = texture2DSRGB(splatMap, $UV);

    texture0 *= splat.r;
    texture1 = mix(texture0,  texture1, splat.g);
    texture2 = mix(texture1, texture2, splat.b);
    // texture3 = mix(texture2, texture3, 1.0 - splat.a);

    // float dist = distance(vec2(0,1), vPositionW.xz);
    // float r = 1.0;
    // float shape = (smoothstep(r-0.1, r, dist)*0.75 + 0.25) - smoothstep(r, r + 0.1, dist);
    // texture3 = mix(texture2, texture3, shape);

    // for(int i=0;i<60;i++){
	// float dist = distance(vec2(0, i + 1), vPositionW.xz);
    // float shape = (smoothstep(r-0.1, r, dist)*0.75 + 0.25) - smoothstep(r, r + 0.1, dist);
    
    // texture3 = mix(texture3, vec4(0, 0.25, 0, 0), shape);

	// }

    dAlbedo = texture2.rgb;    
}
