package org.openstack4j.core.transport.internal;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;

import org.openstack4j.api.exceptions.ConnectionException;
import org.openstack4j.api.exceptions.ResponseException;
import org.openstack4j.core.transport.ClientConstants;
import org.openstack4j.core.transport.HttpExecutorService;
import org.openstack4j.core.transport.HttpRequest;
import org.openstack4j.core.transport.HttpResponse;
import org.openstack4j.openstack.internal.OSAuthenticator;
import org.openstack4j.openstack.internal.OSClientSession;

/**
 * HttpExecutor is the default implementation for HttpExecutorService which is responsible for interfacing with Jersey and mapping common status codes, requests and responses
 * back to the common API
 * 
 * @author Jeremy Unruh
 */
public class HttpExecutor implements HttpExecutorService {

    private static final HttpExecutor INSTANCE = new HttpExecutor();

    private HttpExecutor() { }

    public static HttpExecutor create() {
        return INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> HttpResponse execute(HttpRequest<R> request) {
        try {
            return invoke(request);
        }
        catch (ResponseException re) {
            throw re;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Invokes the given request
     *
     * @param <R> the return type
     * @param request the request to invoke
     * @return the response
     * @throws Exception the exception
     */
    private <R> HttpResponse invoke(HttpRequest<R> request) throws Exception {

        HttpCommand<R> command = HttpCommand.create(request);

        try {
            return invokeRequest(command);
        } catch (ProcessingException pe) {
            throw new ConnectionException(pe.getMessage(), 0, pe);
        } catch (ClientErrorException e) {
            throw HttpResponse.mapException(e.getResponse().getStatusInfo().toString(), e.getResponse().getStatus(), e);
        }
    }

    private <R> HttpResponse invokeRequest(HttpCommand<R> command) throws Exception {
        Response response = command.execute();
        if (command.getRetries() == 0 && response.getStatus() == 401 && !command.getRequest().getHeaders().containsKey(ClientConstants.HEADER_OS4J_AUTH))
        {
            OSAuthenticator.reAuthenticate();
            command.getRequest().getHeaders().put(ClientConstants.HEADER_X_AUTH_TOKEN, OSClientSession.getCurrent().getTokenId());
            return invokeRequest(command.incrementRetriesAndReturn());
        }
        return HttpResponse.wrap(response);
    }
}
