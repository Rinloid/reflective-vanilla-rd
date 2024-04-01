$input v_color0, v_fog, v_texcoord0, v_lightmapUV, relPos, fragPos, frameTime, waterFlag, fogControl

#include <../../include/bgfx_compute.sh>

SAMPLER2D(s_MatTexture,      0);
SAMPLER2D(s_LightMapTexture, 1);
SAMPLER2D(s_SeasonsTexture,  2);

#define DAY_CLOUD_COL   vec3(0.85, 0.89, 1.00)
#define SET_CLOUD_COL   vec3(0.42, 0.32, 0.32)
#define NIGHT_CLOUD_COL vec3(0.07, 0.07, 0.11)

#define DAY_CLOUD_SHADE_COL   vec3(0.67, 0.72, 0.82)
#define SET_CLOUD_SHADE_COL   vec3(0.32, 0.26, 0.27)
#define NIGHT_CLOUD_SHADE_COL vec3(0.05, 0.05, 0.08)

#define DAY_SUN_INNER_COL   vec3(1.0, 1.0, 1.00)
#define SET_SUN_INNER_COL   vec3(1.0, 1.0, 1.00)
#define NIGHT_SUN_INNER_COL vec3(1.0, 1.0, 0.89)

#define DAY_SUN_OUTER_COL   vec3(1.0, 1.0, 1.00)
#define SET_SUN_OUTER_COL   vec3(1.0, 1.0, 0.52)
#define NIGHT_SUN_OUTER_COL vec3(1.0, 1.0, 0.50)

#define DAY_SUN_HALO_COL   vec3(0.61, 0.78, 0.93)
#define SET_SUN_HALO_COL   vec3(0.95, 0.56, 0.15)
#define NIGHT_SUN_HALO_COL vec3(0.30, 0.33, 0.29)

#define DAY_MOON_OUTER_COL   vec3(0.37, 0.4, 0.48)
#define SET_MOON_OUTER_COL   vec3(0.37, 0.4, 0.48)
#define NIGHT_MOON_OUTER_COL vec3(0.37, 0.4, 0.48)

#define DAY_MOON_INNER_COL   vec3(0.85, 0.89, 1.0)
#define SET_MOON_INNER_COL   vec3(0.85, 0.89, 1.0)
#define NIGHT_MOON_INNER_COL vec3(0.85, 0.89, 1.0)

#define DAY_MOON_HALO_COL   vec3(0.15, 0.16, 0.27)
#define SET_MOON_HALO_COL   vec3(0.15, 0.16, 0.27)
#define NIGHT_MOON_HALO_COL vec3(0.15, 0.16, 0.27)

#define DAY_SKY_COL   vec3(0.49, 0.65, 1.00)
#define SET_SKY_COL   vec3(0.15, 0.22, 0.41)
#define NIGHT_SKY_COL vec3(0.00, 0.00, 0.00)

#define DAY_FOG_COL   vec3(0.67, 0.82, 1.00)
#define SET_FOG_COL   vec3(0.64, 0.24, 0.02)
#define NIGHT_FOG_COL vec3(0.02, 0.03, 0.05)

#define CUSTOM_TEXTURE_RESOLUTION ivec2(3072, 2048)

// https://github.com/origin0110/OriginShader
float getTime(vec4 fogCol) {
	return fogCol.g > 0.213101 ? 1.0 : 
		dot(vec4(fogCol.g * fogCol.g * fogCol.g, fogCol.g * fogCol.g, fogCol.g, 1.0), 
			vec4(349.305545, -159.858192, 30.557216, -1.628452));
}

vec3 grayScale(vec3 col) {
    float luma = dot(col, vec3(0.22, 0.707, 0.071));
    
    return vec3(luma, luma, luma);
}

// https://www.shadertoy.com/view/4djSRW
float hash12(vec2 p) {
	vec3 p3  = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);

    return fract((p3.x + p3.y) * p3.z);
}

float hash13(vec3 p3) {
	p3  = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);

    return fract((p3.x + p3.y) * p3.z);
}

float renderStars(vec3 pos, float rain) {
    float stars = 0.0;
    
    vec3 p = floor((pos + 16.0) * 265.0);

    stars = smoothstep(0.9975, 1.0, hash13(p));
    stars = mix(stars, 0.0, rain);

    return stars;
}

float renderSquare(vec3 pos, float size) {
    vec3 p = abs(pos) * size;
    float shape = 1.0 - max(max(p.x, p.z), p.y);

    return step(0.0, shape);
}

vec3 renderSun(vec3 pos, vec3 sunPos, float rain) {
    vec3 sun = vec3(0.0, 0.0, 0.0);

    vec3 p = cross(normalize(pos), sunPos);
    float inner = renderSquare(p, 16.0);
    float outer = renderSquare(p, 12.0);
    p *= 8.0;
    p = floor(p / 0.12) * 0.12;
    float halo = min(1.0, 1.0 / length(p)) * 0.8;

    sun = vec3(inner, outer, halo);
    sun = mix(sun, vec3(0.0, 0.0, 0.0), rain);
    sun = mix(vec3(0.0, 0.0, 0.0), sun, smoothstep(1.0, 0.0, distance(pos, sunPos)));
    
    return sun;
}

float diffuseSphere(vec3 spherePosition, float radius, vec3 lightPosition) {
    float sq = radius * radius - spherePosition.x * spherePosition.x - spherePosition.y * spherePosition.y - spherePosition.z * spherePosition.z;

    if (sq < 0.0) {
        return 0.0;
    } else {
        float z = sqrt(sq);
        vec3 normal = normalize(vec3(spherePosition.yx, z));
		
        return max(0.0, dot(normal, lightPosition));
    }
}

vec2 renderMoon(vec3 pos, vec3 moonPos, float rain, float time) {
    vec2 moon = vec2(0.0, 0.0);

    vec3 lightPosition = vec3(sin(time * 0.03), 0.0, -cos(time * 0.03));

    vec3 p = cross(normalize(pos), moonPos);
    float body = diffuseSphere(floor(p * 66.6) / 66.6, 0.12, lightPosition);
    p *= 8.0;
    p = floor(p / 0.12) * 0.12;
    float halo = min(1.0, 1.0 / length(p)) * 0.8;

    moon = vec2(body, halo);
    moon = mix(moon, vec2(0.0, 0.0), rain);
    moon = mix(vec2(0.0, 0.0), moon, smoothstep(1.0, 0.0, distance(pos, moonPos)));
    
    return moon;
}

float render2DClouds(vec2 pos, float time) {
    vec2 p = pos;
    p.x += time * 0.02;
    float body = hash12(floor(p));
    body = (body > 0.85) ? 1.0 : 0.0;

    return body;
}

vec2 renderThickClouds(vec3 pos, float time) {
    const int steps = 6;
    const float stepSize = 0.016;

    float clouds = 0.0;
    float cHeight = 0.0;

    float drawSpace = smoothstep(0.0, 1.0, length(pos.xz / (pos.y * float(12))));
        if (drawSpace < 1.0 && !bool(step(pos.y, 0.0))) {
        for (int i = 0; i < steps; i++) {
            float height = 1.0 + float(i) * stepSize;
            vec2 cloudPos = pos.xz / pos.y * height;

            cloudPos *= 2.5;
            clouds += render2DClouds(cloudPos, time);

#          if CLOUD_TYPE != 1
                if (i == 0) {
                    cHeight = render2DClouds(cloudPos, time);
                }
#          endif
        }

        clouds = clouds > 0.0 ? 1.0 : 0.0;
        clouds = mix(clouds, 0.0, drawSpace);
    }

    return vec2(clouds, cHeight);
}

vec3 getTexNormal(vec2 uv, float resolution, float scale) {
    vec2 texStep = 1.0 / resolution * vec2(2.0, 1.0);
    float height = dot(texture2DLod(s_MatTexture, uv, 0.0).rgb, vec3(0.4, 0.4, 0.4));
    vec2 dxy = height - vec2(dot(texture2DLod(s_MatTexture, uv + vec2(texStep.x, 0.0), 0.0).rgb, vec3(0.4, 0.4, 0.4)),
        dot(texture2DLod(s_MatTexture, uv + vec2(0.0, texStep.y), 0.0).rgb, vec3(0.4, 0.4, 0.4)));

	return normalize(vec3(dxy * scale / texStep, 1.0));
}

mat3 getTBNMatrix(vec3 normal) {
    vec3 T = vec3(abs(normal.y) + normal.z, 0.0, normal.x);
    vec3 B = vec3(0.0, -abs(normal).x - abs(normal).z, abs(normal).y);
    vec3 N = normal;

    return transpose(mat3(T, B, N));
}

void getCubemapPosAndLayer(in vec3 pos, out vec2 uv, out int layer) {    
    vec3 p = abs(pos);
    float margin;

    if (p.z >= p.x && p.z >= p.y) {
        margin = 0.5 / p.z;
        uv = vec2(pos.z < 0.0 ? -pos.x : pos.x, -pos.y);
        layer = pos.z < 0.0 ? 0 : 2;
    } else if (p.y >= p.x) {
        margin = 0.5 / p.y;
        uv = vec2(pos.x, pos.y < 0.0 ? -pos.z : pos.z);
        layer = pos.y < 0.0 ? 5 : 4;
    } else {
        margin = 0.5 / p.x;
        uv = vec2(pos.x < 0.0 ? pos.z : -pos.z, -pos.y);
        layer = pos.x < 0.0 ? 3 : 1;
    }

    uv = uv * margin + 0.5;
    uv = layer == 4 ? vec2(uv.x, 1.0 - uv.y) : vec2(1.0 - uv.x, uv.y);
}

vec4 getCubemap(vec3 pos) {
    vec4 cubemap = vec4(0.0, 0.0, 0.0, 0.0);

    ivec2 atlasSize = textureSize(s_MatTexture, 0);
    ivec2 cubemapLocation[6];
    cubemapLocation[0] = ivec2(0, 0);
    cubemapLocation[1] = ivec2(1024, 0);
    cubemapLocation[2] = ivec2(2048, 0);
    cubemapLocation[3] = ivec2(0, 1024);
    cubemapLocation[4] = ivec2(1024, 1024);
    cubemapLocation[5] = ivec2(2048, 1024);
    
    vec2 uv;
    int layer;
    getCubemapPosAndLayer(pos, uv, layer);

    cubemap = texelFetch(s_MatTexture, ivec2(uv * vec2(CUSTOM_TEXTURE_RESOLUTION) / vec2(3, 2)) + atlasSize - CUSTOM_TEXTURE_RESOLUTION + cubemapLocation[layer], 0);
    
    return cubemap;
}

void main() {
vec4 albedo = vec4(0.0, 0.0, 0.0, 0.0);
vec4 texCol = vec4(0.0, 0.0, 0.0, 0.0);

#if defined(DEPTH_ONLY_OPAQUE) || defined(DEPTH_ONLY)
    albedo.rgb  = vec3(1.0, 1.0, 1.0);
#else
    albedo = texture2D(s_MatTexture, v_texcoord0);
    texCol = albedo;

#   if defined(ALPHA_TEST) || defined(DEPTH_ONLY)
        if (albedo.a < 0.5) {
            discard;
            return;
        }
#   endif

#   if defined(SEASONS) && (defined(OPAQUE) || defined(ALPHA_TEST))
        albedo.rgb *= mix(vec3(1.0, 1.0, 1.0), texture2D(s_SeasonsTexture, v_color0.xy).rgb * 2.0, v_color0.b);
        albedo.rgb *= v_color0.aaa;
#   else
        albedo.rgb *= v_color0.rgb;
#   endif
#endif

#ifndef TRANSPARENT
    albedo.a = 1.0;
#endif

bool isBlend = false;
#if defined (TRANSPARENT)
    isBlend = albedo.a < 1.0 && albedo.a > 0.0 ? true : false;
#else
    isBlend = false;
#endif

bool isReflective = false;
#if !defined(ALPHA_TEST) && !defined(TRANSPARENT)
	if ((0.95 < texCol.a && texCol.a < 1.0) && v_color0.b == v_color0.g && v_color0.r == v_color0.g) {
		isReflective = true;
	}
#endif

vec3 worldNormal = normalize(cross(dFdx(fragPos), dFdy(fragPos)));
vec3 texNormal   = getTexNormal(v_texcoord0, 1024.0, 0.0003);
vec3 totalNormal = mul(texNormal, getTBNMatrix(worldNormal));
float time = getTime(v_fog);
vec3 sunPos = vec3(cos(time), sin(time), 0.0);
vec3 moonPos = -sunPos;
float daylight = max(0.0, time);
float set = min(smoothstep(0.0, 0.2, time), smoothstep(0.6, 0.3, time));
float rain = mix(smoothstep(0.5, 0.3, fogControl.x), 0.0, step(fogControl.x, 0.0));
float underwater = step(fogControl.x, 0.0);

vec3 skyCol = mix(mix(NIGHT_SKY_COL, DAY_SKY_COL, daylight), SET_SKY_COL, set);
vec3 fogCol = mix(mix(NIGHT_FOG_COL, DAY_FOG_COL, daylight), SET_FOG_COL, set);
vec3 totalFogCol = mix(skyCol, fogCol, smoothstep(0.8, 1.0, 1.0 - normalize(relPos).y));
totalFogCol = mix(totalFogCol, grayScale(totalFogCol) * 0.325, rain);

vec3 skyPos = reflect(normalize(relPos), totalNormal);
vec2 clouds = renderThickClouds(skyPos, frameTime);
float stars = renderStars(skyPos, rain);
vec3 sun = renderSun(skyPos, sunPos, rain);
vec2 moon = renderMoon(skyPos, moonPos, rain, frameTime) * (1.0 - daylight);

vec3 sunHaloCol = mix(mix(NIGHT_SUN_HALO_COL, DAY_SUN_HALO_COL, daylight), SET_SUN_HALO_COL, set);
vec3 sunOuterCol = mix(mix(NIGHT_SUN_OUTER_COL, DAY_SUN_OUTER_COL, daylight), SET_SUN_OUTER_COL, set);
vec3 sunInnerCol = mix(mix(NIGHT_SUN_INNER_COL, DAY_SUN_INNER_COL, daylight), SET_SUN_INNER_COL, set);

vec3 moonHaloCol = mix(mix(NIGHT_MOON_HALO_COL, DAY_MOON_HALO_COL, daylight), SET_MOON_HALO_COL, set);
vec3 moonOuterCol = mix(mix(NIGHT_MOON_OUTER_COL, DAY_MOON_OUTER_COL, daylight), SET_MOON_OUTER_COL, set);
vec3 moonInnerCol = mix(mix(NIGHT_MOON_INNER_COL, DAY_MOON_INNER_COL, daylight), SET_MOON_INNER_COL, set);

vec3 cloudCol = mix(mix(NIGHT_CLOUD_COL, DAY_CLOUD_COL, daylight), SET_CLOUD_COL, set);
vec3 cloudShadeCol = mix(mix(NIGHT_CLOUD_SHADE_COL, DAY_CLOUD_SHADE_COL, daylight), SET_CLOUD_SHADE_COL, set);

vec3 totalSkyCol = mix(skyCol, fogCol, smoothstep(0.8, 1.0, 1.0 - skyPos.y));
vec3 totalCloudCol = mix(cloudCol, cloudShadeCol, clouds.y);

vec3 totalSunCol = totalSkyCol;
totalSunCol = mix(totalSunCol, sunHaloCol, sun.z);
totalSunCol = mix(totalSunCol, sunOuterCol, sun.y);
totalSunCol = mix(totalSunCol, sunInnerCol, sun.x);
vec3 totalMoonCol = totalSkyCol;
totalMoonCol = mix(totalMoonCol, moonHaloCol, moon.y);
totalMoonCol = mix(totalMoonCol, moonInnerCol, moon.x);
totalMoonCol = mix(totalMoonCol, totalCloudCol, clouds.x * 0.5);

vec3 totalSky = mix(totalSkyCol, totalCloudCol, clouds.x);
totalSky = mix(totalSky, grayScale(totalSky) * 0.325, rain);

totalSky = mix(vec3(stars, stars, stars), totalSky, daylight);
totalSky = mix(totalSky, totalSunCol, sun.z * 1.25);
totalSky = mix(totalSky, totalMoonCol, moon.y * 1.25);

vec4 cubemap = getCubemap(skyPos);


totalSky = mix(mix(albedo.rgb, totalSky, smoothstep(0.6, 0.94, v_lightmapUV.y)), cubemap.rgb, cubemap.a);

if ((isBlend || isReflective || bool(waterFlag))) {
    float cosTheta = 1.0 - abs(dot(normalize(relPos), totalNormal));
    float fresnel = clamp(bool(waterFlag) ? cosTheta * 1.1 : isBlend ? cosTheta * 1.0 : cosTheta * 0.85, 0.0, 1.0);
    albedo.rgb = mix(albedo.rgb, totalSky, fresnel * fresnel * fresnel * fresnel * fresnel);

    if (isBlend || bool(waterFlag)) {
        float F = cosTheta * cosTheta * cosTheta * cosTheta * cosTheta;
        albedo.a = mix(bool(waterFlag) ? 0.4 : albedo.a, 1.0, F);
    }
}

albedo.rgb *= texture2D(s_LightMapTexture, v_lightmapUV).rgb;

if ((isBlend || isReflective || bool(waterFlag))) {
    albedo = clamp(albedo + vec4(totalSunCol, 1.0) * sun.z * smoothstep(0.6, 0.94, v_lightmapUV.y) * (1.0 - cubemap.a), 0.0, 1.0);
    albedo = clamp(albedo + vec4(totalMoonCol, 1.0) * 0.5 * moon.x * smoothstep(0.6, 0.94, v_lightmapUV.y) * (1.0 - cubemap.a), 0.0, 1.0);
}

albedo.rgb = mix(albedo.rgb, v_fog.rgb, v_fog.a);

    gl_FragColor = albedo;
}
