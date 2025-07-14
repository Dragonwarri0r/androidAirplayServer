package com.github.serezhka.airplay.server.internal.handler.session;


import java.util.HashMap;
import java.util.Map;

public class SessionManager {

    private final Map<String, Session> sessions = new HashMap<>();

    public Session getSession(String sessionId) {
        synchronized (sessions) {
            Session session;
            if ((session = sessions.get(sessionId)) == null) {
                session = new Session(sessionId);
                sessions.put(sessionId, session);
            }
            return session;
        }
    }
}
