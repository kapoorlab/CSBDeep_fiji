package de.csbdresden.csbdeep.network;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import de.csbdresden.csbdeep.network.model.Network;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.tensorflow.TensorFlowService;
import net.imglib2.type.numeric.real.FloatType;

public class InputMapperTest {

	private void doMapping(Dataset input, Network network) {
		network.loadInputNode(input);
		network.loadOutputNode(input);
		network.initMapping();
		new DefaultInputMapper().run(input, network);
		network.calculateMapping();
		System.out.println("INPUT:");
		network.getInputNode().printMapping();
		System.out.println("OUTPUT:");
		network.getOutputNode().printMapping();
	}

	@Test
	public void network2D2D_input2D() {
		ImageJ ij = new ImageJ();
		Dataset input = ij.dataset().create(new FloatType(), new long[]{2,3}, "", new AxisType[]{Axes.X, Axes.Y});
		Network network = new TestNetworkSingle2D2D();
		ij.context().inject(network);

		doMapping(input, network);

		assertEquals(Axes.TIME, network.getInputNode().getNodeAxis(0));
		assertEquals(Axes.Y, network.getInputNode().getNodeAxis(1));
		assertEquals(Axes.X, network.getInputNode().getNodeAxis(2));
		assertEquals(Axes.CHANNEL, network.getInputNode().getNodeAxis(3));

		assertEquals(Axes.TIME, network.getOutputNode().getNodeAxis(0));
		assertEquals(Axes.Y, network.getOutputNode().getNodeAxis(1));
		assertEquals(Axes.X, network.getOutputNode().getNodeAxis(2));
		assertEquals(Axes.CHANNEL, network.getOutputNode().getNodeAxis(3));

		assertEquals(4, network.getInputNode().getMappingIndices().length);
		assertEquals(2, network.getInputNode().getMappingIndices()[0]);
		assertEquals(1, network.getInputNode().getMappingIndices()[1]);
	}

	@Test
	public void network2D2D_input3D() {
		ImageJ ij = new ImageJ();
		Dataset input = ij.dataset().create(new FloatType(), new long[]{2,3,4}, "", new AxisType[]{Axes.X, Axes.Y, Axes.Z});
		Network network = new TestNetworkSingle2D2D();
		ij.context().inject(network);

		doMapping(input, network);

		assertEquals(Axes.Z, network.getInputNode().getNodeAxis(0));
		assertEquals(Axes.Y, network.getInputNode().getNodeAxis(1));
		assertEquals(Axes.X, network.getInputNode().getNodeAxis(2));
		assertEquals(Axes.CHANNEL, network.getInputNode().getNodeAxis(3));

		assertEquals(Axes.Z, network.getOutputNode().getNodeAxis(0));
		assertEquals(Axes.Y, network.getOutputNode().getNodeAxis(1));
		assertEquals(Axes.X, network.getOutputNode().getNodeAxis(2));
		assertEquals(Axes.CHANNEL, network.getOutputNode().getNodeAxis(3));

		assertEquals(4, network.getInputNode().getMappingIndices().length);
		assertEquals(2, network.getInputNode().getMappingIndices()[0]);
		assertEquals(1, network.getInputNode().getMappingIndices()[1]);
		assertEquals(0, network.getInputNode().getMappingIndices()[2]);
	}

	@Test
	public void network3D2D_input3D() {
		ImageJ ij = new ImageJ();
		Dataset input = ij.dataset().create(new FloatType(), new long[]{2,3,4}, "", new AxisType[]{Axes.X, Axes.Y, Axes.Z});
		Network network = new TestNetworkSingle3D2D();
		ij.context().inject(network);

		doMapping(input, network);

		assertEquals(Axes.TIME, network.getInputNode().getNodeAxis(0));
		assertEquals(Axes.Z, network.getInputNode().getNodeAxis(1));
		assertEquals(Axes.Y, network.getInputNode().getNodeAxis(2));
		assertEquals(Axes.X, network.getInputNode().getNodeAxis(3));
		assertEquals(Axes.CHANNEL, network.getInputNode().getNodeAxis(4));

		assertEquals(Axes.TIME, network.getOutputNode().getNodeAxis(0));
		assertEquals(Axes.Y, network.getOutputNode().getNodeAxis(1));
		assertEquals(Axes.X, network.getOutputNode().getNodeAxis(2));
		assertEquals(Axes.CHANNEL, network.getOutputNode().getNodeAxis(3));

		assertEquals(5, network.getInputNode().getMappingIndices().length);
		assertEquals(3, network.getInputNode().getMappingIndices()[0]);
		assertEquals(2, network.getInputNode().getMappingIndices()[1]);
		assertEquals(1, network.getInputNode().getMappingIndices()[2]);
	}

	@Test
	public void single3D3DTest() {
		ImageJ ij = new ImageJ();
		Dataset input = ij.dataset().create(new FloatType(), new long[]{2,3,4}, "", new AxisType[]{Axes.X, Axes.Y, Axes.Z});
		Network network = new TestNetworkSingle3D3D();
		ij.context().inject(network);

		doMapping(input, network);

		assertEquals(Axes.TIME, network.getInputNode().getNodeAxis(0));
		assertEquals(Axes.Z, network.getInputNode().getNodeAxis(1));
		assertEquals(Axes.Y, network.getInputNode().getNodeAxis(2));
		assertEquals(Axes.X, network.getInputNode().getNodeAxis(3));
		assertEquals(Axes.CHANNEL, network.getInputNode().getNodeAxis(4));

		assertEquals(Axes.TIME, network.getOutputNode().getNodeAxis(0));
		assertEquals(Axes.Z, network.getOutputNode().getNodeAxis(1));
		assertEquals(Axes.Y, network.getOutputNode().getNodeAxis(2));
		assertEquals(Axes.X, network.getOutputNode().getNodeAxis(3));
		assertEquals(Axes.CHANNEL, network.getOutputNode().getNodeAxis(4));

		assertEquals(5, network.getInputNode().getMappingIndices().length);
		assertEquals(3, network.getInputNode().getMappingIndices()[0]);
		assertEquals(2, network.getInputNode().getMappingIndices()[1]);
		assertEquals(1, network.getInputNode().getMappingIndices()[2]);
	}
}
