attribute vec3 aPosition;
attribute vec2 aUv0;

varying vec2 vUv0;
varying vec3 WorldPosition;

uniform mat4 matrix_model;
uniform mat4 matrix_viewProjection;

uniform float uTime;

void main(void)
{
    vUv0 = aUv0;   
    vec3 pos = aPosition;

    pos.y +=  cos(pos.z*5.0+uTime) * 0.04 * sin(pos.x * 5.0 + uTime);
    
    gl_Position = matrix_viewProjection * matrix_model * vec4(pos, 1.0);    
    
    WorldPosition = pos;
}
