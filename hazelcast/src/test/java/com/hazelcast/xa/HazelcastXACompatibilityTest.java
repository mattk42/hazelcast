package com.hazelcast.xa;

import com.atomikos.datasource.xa.XID;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.TransactionalMap;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.transaction.HazelcastXAResource;
import com.hazelcast.transaction.TransactionContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import static javax.transaction.xa.XAResource.TMNOFLAGS;
import static javax.transaction.xa.XAResource.TMSUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class HazelcastXACompatibilityTest extends HazelcastTestSupport {

    private HazelcastInstance instance, secondInstance;
    private HazelcastXAResource xaResource, secondXaResource;
    private Xid xid;

    private static Xid createXid() throws InterruptedException {
        return new XID(randomString(), "test");
    }

    @Before
    public void setUp() throws Exception {
        TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(2);
        instance = factory.newHazelcastInstance();
        secondInstance = factory.newHazelcastInstance();
        xaResource = instance.getXAResource();
        secondXaResource = secondInstance.getXAResource();
        xid = createXid();
    }

    @Test
    public void testRecoveryRequiresRollbackOfPreparedXidOnSecondXAResource() throws Exception {
        doSomeWorkWithXa(xaResource);
        performPrepareWithXa(xaResource);
        performRollbackWithXa(secondXaResource);
    }

    @Test
    public void testRecoveryRequiresCommitOfPreparedXidOnSecondXAResource() throws Exception {
        doSomeWorkWithXa(xaResource);
        performPrepareWithXa(xaResource);
        performCommitWithXa(secondXaResource);
    }

    @Test
    public void testRecoveryReturnsPreparedXidOnSecondXAResource() throws Exception {
        doSomeWorkWithXa(xaResource);
        performPrepareWithXa(xaResource);
        assertRecoversXid(secondXaResource);
    }

    @Test
    public void testRecoveryReturnsPreparedXidOnXAResource() throws Exception {
        doSomeWorkWithXa(xaResource);
        performPrepareWithXa(xaResource);
        assertRecoversXid(xaResource);
    }

    private void assertRecoversXid(XAResource xaResource) throws XAException {
        Xid[] xids = xaResource.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN);
        assertTrue("" + xids.length, xids.length == 1);
    }

    @Test
    public void testRecoveryRequiresRollbackOfUnknownXid() throws Exception {
        performRollbackWithXa(xaResource);
    }

    @Test
    public void testIsSameRm() throws Exception {
        assertTrue(xaResource.isSameRM(secondXaResource));
    }

    private void performCommitWithXa(XAResource xaResource) throws XAException {
        xaResource.commit(xid, false);
    }

    private void performRollbackWithXa(XAResource secondXaResource) throws XAException {
        try {
            secondXaResource.rollback(xid);
        } catch (XAException xaerr) {
            assertTrue("rollback of unknown xid gives unexpected errorCode: " + xaerr.errorCode, ((XAException.XA_RBBASE <= xaerr.errorCode) && (xaerr.errorCode <= XAException.XA_RBEND))
                    || xaerr.errorCode == XAException.XAER_NOTA);
        }
    }

    private void doSomeWorkWithXa(HazelcastXAResource xaResource) throws Exception {
        xaResource.start(xid, TMNOFLAGS);
        TransactionContext context = xaResource.getTransactionContext();
        TransactionalMap<Object, Object> map = context.getMap("map");
        map.put("key", "value");
        xaResource.end(xid, XAResource.TMSUCCESS);
    }

    private void performPrepareWithXa(XAResource xaResource) throws XAException {
        xaResource.prepare(xid);
    }

    @Test
    public void testRecoveryAllowedAtAnyTime() throws Exception {
        recover(xaResource);
        doSomeWorkWithXa(xaResource);
        recover(xaResource);
        performPrepareWithXa(xaResource);
        recover(xaResource);
        performCommitWithXa(xaResource);
        recover(xaResource);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testManualBeginShouldThrowException() throws Exception {
        xaResource.start(xid, TMNOFLAGS);
        TransactionContext transactionContext = xaResource.getTransactionContext();
        transactionContext.beginTransaction();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testManualCommitShouldThrowException() throws Exception {
        xaResource.start(xid, TMNOFLAGS);
        TransactionContext transactionContext = xaResource.getTransactionContext();
        transactionContext.commitTransaction();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testManualRollbackShouldThrowException() throws Exception {
        xaResource.start(xid, TMNOFLAGS);
        TransactionContext transactionContext = xaResource.getTransactionContext();
        transactionContext.rollbackTransaction();
    }

    private void recover(XAResource xaResource) throws XAException {
        xaResource.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN);
    }

    @Test
    public void testTransactionTimeout() throws XAException {
        boolean timeoutSet = xaResource.setTransactionTimeout(2);
        assertTrue(timeoutSet);
        xaResource.start(xid, TMNOFLAGS);
        TransactionContext context = xaResource.getTransactionContext();
        TransactionalMap<Object, Object> map = context.getMap("map");
        map.put("key", "val");
        xaResource.end(xid, TMSUCCESS);

        sleepSeconds(3);

        try {
            xaResource.commit(xid, true);
            fail();
        } catch (XAException e) {
            assertEquals(XAException.XA_RBTIMEOUT, e.errorCode);
        }
    }

}
