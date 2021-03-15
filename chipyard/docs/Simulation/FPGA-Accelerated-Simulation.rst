.. _firesim-sim-intro:

FPGA-Accelerated Simulation
==============================

FireSim
-----------------------

`FireSim <https://fires.im/>`__ is an open-source cycle-accurate FPGA-accelerated full-system hardware simulation platform that runs on cloud FPGAs (Amazon EC2 F1).
FireSim allows RTL-level simulation at orders-of-magnitude faster speeds than software RTL simulators.
FireSim also provides additional device models to allow full-system simulation, including memory models and network models.

FireSim currently supports running only on Amazon EC2 F1 FPGA-enabled virtual instances.
In order to simulate your Chipyard design using FireSim, if you have not
already, follow the initial EC2 setup instructions as detailed in the `FireSim
documentation  <http://docs.fires.im/en/latest/Initial-Setup/index.html>`__.
Then clone Chipyard onto your FireSim manager
instance, and setup your Chipyard repository as you would normally.

Next, initalize FireSim as a library in Chipyard by running:

.. code-block:: shell

    # At the root of your chipyard repo
    ./scripts/firesim-setup.sh --fast

``firesim-setup.sh`` initializes additional submodules and then invokes
firesim's ``build-setup.sh`` script adding ``--library`` to properly
initialize FireSim as a library submodule in chipyard. You may run
``./sims/firesim/build-setup.sh --help`` to see more options.

Finally, source the following environment at the root of the firesim directory:

.. code-block:: shell

    cd sims/firesim
    # (Recommended) The default manager environment (includes env.sh)
    source sourceme-f1-manager.sh

.. Note:: Every time you want to use FireSim with a fresh shell, you must source this ``sourceme-f1-manager.sh``

At this point you're ready to use FireSim with Chipyard. If you're not already
familiar with FireSim, please return to the `FireSim Docs
<https://docs.fires.im/en/latest/Initial-Setup/Setting-up-your-Manager-Instance.html#completing-setup-using-the-manager>`__,
and proceed with the rest of the tutorial.

Running your Design in FireSim
------------------------------
Converting a Chipyard config (one in ``chipyard/src/main/scala`` to run in FireSim is simple. We are using the same target (top) RTL, and only need to specify a new set of connection behaviors for the IOs of that module. Simply create a matching config within ``generators/firechip/src/main/scala/TargetConfigs`` that inherits your config defined in ``chipyard``.


.. literalinclude:: ../../generators/firechip/src/main/scala/TargetConfigs.scala
    :language: scala
    :start-after: DOC include start: firesimconfig
    :end-before: DOC include end: firesimconfig


Only 3 additional config fragments are needed.

* ``WithFireSimConfigTweaks`` modifies your design to better fit the FireSim usage model. For example, FireSim designs typically include a UART. Technically, adding this in is optional, but *strongly* recommended.
* ``WithDefaultMemModel`` sets the external memory model in the FireSim simulation. See the FireSim documentation for details.
* ``WithDefaultFireSimBridges`` sets the ``IOBinders`` key to use FireSim's Bridge system, which can drive target IOs with software bridge models running on the simulation host. See the FireSim documentation for details.
