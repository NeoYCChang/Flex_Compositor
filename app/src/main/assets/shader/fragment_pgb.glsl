#extension GL_OES_EGL_image_external : require
precision mediump float;

#define PI 3.1415926
#define TWO_PI 6.2831853

//draw progress bar
uniform vec2 uCenter;
uniform float uAngle;

/** atan
           ℼ/2
          *****
       ***     ***
    ℼ **         ** 0
       ***     ***
          *****
          -ℼ/2
 */


/**
 * Determines whether the current fragment should draw the "head" (cap) of a progress arc.
 *
 * @param r_max  Outer arc radius
 * @param r_min  Inner arc radius
 * @param a_end  Angle (in radians) of the end point of the arc
 * @param angle  Angle (in radians) between current fragment and center point
 * @param coord  Coordinates of the current fragment (gl_FragCoord.xy)
 * @return       true if the fragment is within the head area and should be drawn; false otherwise
 */
bool drawHead(float r_max, float r_min, float a_end, float angle, vec2 coord) {
    float r_head = (r_max - r_min) / 2.0;
    float r_mid = (r_min+r_max)/2.0;
    float a_head_range = r_head / r_mid;
    float a_head = a_end - a_head_range;

    if(a_head > PI){
        a_head = a_head - TWO_PI;
    }
    vec2 center_head = vec2(r_mid * cos(a_head) + uCenter.x, r_mid * sin(a_head) + uCenter.y);


    // Check whether the current fragment lies within the arc head region
    if(a_head >= 0.0 && a_end < 0.0){
        if(angle > a_head && angle <= PI){
            float dist_head = distance(coord, center_head);
            if(r_head < dist_head){
                return false;
            }
        }
        else if(angle > -PI && angle < a_end){
            float dist_head = distance(coord, center_head);
            if(r_head < dist_head){
                return false;
            }
        }
    }
    else{
        if(angle > a_head && angle < a_end){
            float dist_head = distance(coord, center_head);
            if(r_head < dist_head){
                return false;
            }
        }
    }

    return true;
}

void main() {
    bool inRange = false;
    float dist = distance(gl_FragCoord.xy, uCenter);
    float min_l = min(uCenter.x, uCenter.y);
    float r_min = min_l * 0.5;
    float r_max = min_l * 0.55;
    float alpha = 0.0;

    if(dist > r_min && dist < r_max){
        vec2 dir = gl_FragCoord.xy - uCenter;
        float angle = atan(dir.y, dir.x);
        float a_start = uAngle;
        float a_end = uAngle+1.5;

        if(a_end > PI){
            a_end = a_end - TWO_PI;
        }


        // Check whether the current fragment lies within the progress bar region
        if(a_start >= 0.0 && a_end < 0.0){
            if(angle > a_start && angle <= PI){
                inRange = true;
                alpha = (angle - a_start)/1.5;
            }
            else if(angle > -PI && angle < a_end){
                inRange = true;
                alpha = (angle + TWO_PI - a_start)/1.5;
            }
        }
        else{
            if(angle > a_start && angle < a_end){
                inRange = true;
                alpha = (angle - a_start)/1.5;
            }
        }
        if(inRange){
            inRange = drawHead(r_max, r_min, a_end, angle, gl_FragCoord.xy);
        }

    }


    if (inRange) {
        gl_FragColor = vec4(0.92, 0.43, 0.20, alpha);
    } else {
        discard;
    }
}