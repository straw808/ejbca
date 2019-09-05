/*************************************************************************
 *                                                                       *
 *  EJBCA - Proprietary Modules: Enterprise Certificate Authority        *
 *                                                                       *
 *  Copyright (c), PrimeKey Solutions AB. All rights reserved.           *
 *  The use of the Proprietary Modules are subject to specific           * 
 *  commercial license terms.                                            *
 *                                                                       *
 *************************************************************************/
package org.ejbca.ui.p11ngcli.helper;

/**
 * 
 * @version $Id$
 *
 */
public abstract class OperationsThread  extends Thread {
    FailureCallback failureCallback;
    private volatile boolean stop;
    private int numOperations;
    
    public OperationsThread(final FailureCallback failureCallback) {
        this.failureCallback = failureCallback;
    }
    
    /**
     * Indicate that this thread has discovered a failure.
     * @param message A description of the problem
     * @throws Exception 
     */
    protected void fireFailure(final String message) throws Exception {
        failureCallback.failed(this, message);
    }
    
    public void stopIt() {
        stop = true;
    }
    
    public boolean isStop() {
        return stop;
    }
    
    public int getNumberOfOperations() {
        return numOperations;
    }
    
    public void registerOperation() {
        numOperations++;
    }
}
