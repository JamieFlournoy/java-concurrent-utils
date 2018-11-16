package com.pervasivecode.utils.concurrent.chute.api;

/**
 * A chute is a blocking queue that you can close. After being closed, a chute will not accept new
 * elements.
 */
public interface Chute<E> extends ChuteEntrance<E>, ChuteExit<E> {
  // This interface is just composed of ChuteEntrance and ChuteExit.
}
