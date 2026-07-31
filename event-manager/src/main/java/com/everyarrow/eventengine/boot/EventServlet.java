package com.everyarrow.eventengine.boot;

import com.everyarrow.eventengine.core.adapters.IncomingEvent;
import com.everyarrow.eventengine.core.adapters.ServiceCallMapper;
import com.everyarrow.eventengine.core.adapters.external.http.MutableHttpRequest;
import com.everyarrow.eventengine.core.adapters.external.http.RequestBodyTooLargeException;
import com.everyarrow.eventengine.core.adapters.external.http.RestIncomingEvent;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * REST entry point for the Event Engine.
 *
 * Converts HttpServletRequest → IncomingEvent → ServiceCall → Engine.execute()
 */
public class EventServlet extends HttpServlet {

    private static volatile Engine engine;

    public static void setEngine(Engine e) {
        if (e == null) {
            throw new IllegalArgumentException("Engine must not be null");
        }
        engine = e;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
        Engine e = engine;
        if (e == null) {
            try {
                resp.setStatus(500);
                resp.setContentType("application/json");
                resp.getWriter().write("{\"error\":\"Engine not initialized\"}");
            } catch (Exception ignore) {}
            return;
        }

        try {
            // Convert raw HTTP request into normalized IncomingEvent
            IncomingEvent event = new RestIncomingEvent(new MutableHttpRequest(req));



            // Perform service routing: IncomingEvent → ServiceCall
            ServiceCall call = ServiceCallMapper.map(event);

            //Lets add some security with the combined Authorizer
            //



            // Execute service via engine
            ServiceCallResponse result = e.execute(call);




            resp.setStatus(result.getHttpStatus());
            resp.setContentType("application/json");
            resp.getWriter().write(result.toJson());

        } catch (RequestBodyTooLargeException ex) {
            try {
                resp.setStatus(413);
                resp.setContentType("application/json");
                resp.getWriter().write("{\"error\":\"Request body too large\"}");
            } catch (Exception ignore) {
            }
        } catch (Exception ex) {
            try {
                resp.setStatus(500);
                resp.setContentType("application/json");
                resp.getWriter().write("{\"error\":\"Internal server error\"}");
            } catch (Exception ignore) {
            }
        }
    }
}
