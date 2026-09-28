/**
 * TARGET Services — RTGS + ESMIG: the foundation where central bank money (CeBM) lives.
 * Holds the participants' accounts (DCA) and settles transfers on a gross, real-time basis.
 * No dependencies: this is the stable, critical core that we do not rewrite.
 */
@org.springframework.modulith.ApplicationModule(displayName = "TARGET Services (RTGS)", allowedDependencies = {})
package com.dl3s.pontes.rtgs;
