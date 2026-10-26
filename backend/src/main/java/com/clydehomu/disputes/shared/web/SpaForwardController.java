package com.clydehomu.disputes.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Lets the Vue router use history mode: a browser reload or deep link on a client-side route gets the SPA shell, and the
 * router takes over from there.
 *
 * <p>A route qualifies when no segment contains a dot, so real files such as {@code /assets/app.js} keep going to the
 * static resource handler, and the first segment is not one of the server-owned prefixes. Four segments cover the
 * deepest SPA route ({@code /transactions/{id}/dispute}); anything deeper is not a client route.
 */
@Controller
class SpaForwardController {

    private static final String FIRST = "/{first:(?!api$|actuator$|v3$|swagger-ui$)[^.]+}";
    private static final String SECOND = "/{second:[^.]+}";
    private static final String THIRD = "/{third:[^.]+}";
    private static final String FOURTH = "/{fourth:[^.]+}";

    @GetMapping({"/", FIRST, FIRST + SECOND, FIRST + SECOND + THIRD, FIRST + SECOND + THIRD + FOURTH})
    String forwardToSpa() {
        return "forward:/index.html";
    }
}
