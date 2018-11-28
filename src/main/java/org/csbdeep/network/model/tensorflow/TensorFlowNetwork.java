
package org.csbdeep.network.model.tensorflow;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.csbdeep.network.DefaultInputMapper;
import org.csbdeep.network.model.DefaultNetwork;
import org.csbdeep.network.model.NetworkSettings;
import org.csbdeep.task.Task;
import org.scijava.io.location.Location;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.TensorFlow;
import org.tensorflow.TensorFlowException;
import org.tensorflow.framework.MetaGraphDef;
import org.tensorflow.framework.SignatureDef;
import org.tensorflow.framework.TensorInfo;
import org.tensorflow.framework.TensorShapeProto;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.protobuf.InvalidProtocolBufferException;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.tensorflow.TensorFlowService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public class TensorFlowNetwork<T extends RealType<T>> extends
		DefaultNetwork<T>
{

	private SavedModelBundle model;
	private SignatureDef sig;
	private Map meta;
	private final TensorFlowService tensorFlowService;
	private final DatasetService datasetService;
	private TensorInfo inputTensorInfo, outputTensorInfo;
	private boolean foundJNI = true;
	private boolean gpuSupport = false;
	protected boolean isDoingDimensionReduction = false;
	protected AxisType axisToRemove;
	// Same as
	// tf.saved_model.signature_constants.DEFAULT_SERVING_SIGNATURE_DEF_KEY
	// in Python. Perhaps this should be an exported constant in TensorFlow's Java
	// API.
	private static final String MODEL_TAG = "serve";
	protected static final String DEFAULT_SERVING_SIGNATURE_DEF_KEY =
		"serving_default";

	public TensorFlowNetwork(TensorFlowService tensorFlowService,
		DatasetService datasetService, Task associatedTask)
	{
		super(associatedTask);
		this.tensorFlowService = tensorFlowService;
		this.datasetService = datasetService;
		log("imagej-tensorflow version: " + tensorFlowService.getVersion());
		try {
			log("tensorflow version: " + TensorFlow.version());
		}
		catch (final UnsatisfiedLinkError e){
			foundJNI = false;
			logError("Couldn't load TensorFlow.\n" +
					"By default, CSBDeep will load TensorFlow for CPU. " +
					"If you added a CSBDeep update site for CUDA " +
					"to get GPU support, make sure you have the matching CUDA and cuDNN " +
					"versions installed. \nIf you want to use the CPU, make sure " +
					"the GPU tensorflow library file is removed from the lib folder.");
			e.printStackTrace();
		}
	}

	@Override
	public void testGPUSupport() {
		log("The current library path is: LD_LIBRARY_PATH=" + System.getenv(
			"LD_LIBRARY_PATH"));
		try {
			System.loadLibrary("tensorflow_jni");
			gpuSupport = true;
		}
		catch (final UnsatisfiedLinkError e) {
			gpuSupport = false;
		}
		if (!gpuSupport) {
			log("Couldn't load tensorflow GPU support.");
			log(
				"If the problem is CUDA related, make sure CUDA and cuDNN are in the LD_LIBRARY_PATH.");
			log("Using CPU version from jar file.");
		}
	}

	@Override
	public void loadInputNode(final Dataset dataset) {
		super.loadInputNode( dataset);
		if (sig != null && sig.getInputsCount() > 0) {
			inputNode.setName(sig.getInputsMap().keySet().iterator().next());
			setInputTensor(sig.getInputsOrThrow(inputNode.getName()));
			inputNode.setNodeShape(getShape(getInputTensorInfo().getTensorShape()));
			inputNode.initializeNodeMapping();
		}
	}

	@Override
	public void loadOutputNode(Dataset dataset) {
		super.loadOutputNode(dataset);
		if (sig != null && sig.getOutputsCount() > 0) {
			outputNode.setName(sig.getOutputsMap().keySet().iterator().next());
			setOutputTensor(sig.getOutputsOrThrow(outputNode.getName()));
			outputNode.setNodeShape(getShape(getOutputTensorInfo().getTensorShape()));
			outputNode.initializeNodeMapping();
		}
	}

	private long[] getShape(final TensorShapeProto tensorShape) {
		final long[] shape = new long[tensorShape.getDimCount()];
		for (int i = 0; i < shape.length; i++) {
			shape[i] = tensorShape.getDim(i).getSize();
		}
		return shape;
	}

	@Override
	protected boolean loadModel(final Location source, final String modelName) {
		if(!foundJNI) return false;
		log("Loading TensorFlow model " + modelName + " from source file " + source.getURI());
		try {
			if (model != null) {
				model.close();
			}
			model = tensorFlowService.loadModel(source, modelName, MODEL_TAG);
			loadNetworkSettingsFromJson(tensorFlowService.loadFile(source, modelName, "meta.json"));
		}
		catch (TensorFlowException | IOException e) {
			e.printStackTrace();
			return false;
		}
		// Extract names from the model signature.
		// The strings "input", "probabilities" and "patches" are meant to be
		// in sync with the model exporter (export_saved_model()) in Python.
		try {
			sig = MetaGraphDef.parseFrom(model.metaGraphDef()).getSignatureDefOrThrow(
				DEFAULT_SERVING_SIGNATURE_DEF_KEY);
		}
		catch (final InvalidProtocolBufferException e) {
			 e.printStackTrace();
		}
		return true;
	}

	private void loadNetworkSettingsFromJson(File jsonFile) {
		networkSettings = new NetworkSettings();
		try {
			JsonReader reader = new JsonReader(new FileReader(jsonFile));
			try {
				readNetworkSettingsArray(reader);
			} finally {
				reader.close();
			}
		} catch (IOException e) {
			log("No meta.json file found for network.");
		}
	}

	private void readNetworkSettingsArray(JsonReader reader) throws IOException {
		reader.beginObject();
		while (reader.hasNext()) {
			String name = reader.nextName();
			if (name.equals("axes_div_by") && reader.peek() != JsonToken.NULL) {
				networkSettings.axesDivBy = readIntArray(reader);
			} else if (name.equals("tile_overlap") && reader.peek() != JsonToken.NULL) {
				networkSettings.tileOverlap = readIntArray(reader);
			} else if (name.equals("axes") && reader.peek() != JsonToken.NULL) {
				networkSettings.axesIn = readAxesString(reader);
			} else if (name.equals("axes_out") && reader.peek() != JsonToken.NULL) {
				networkSettings.axesOut = readAxesString(reader);
			} else if (name.equals("tiling") && reader.peek() != JsonToken.NULL) {
				networkSettings.tilingAllowed = readBooleanArray(reader);
			} else {
				reader.skipValue();
			}
		}
		reader.endObject();
	}

	private List readAxesString(JsonReader reader) throws IOException {
		String singleEntry = reader.nextString();
		return toAxesList(singleEntry);
	}

	private List<AxisType> toAxesList(String axesStr) {
		return DefaultInputMapper.parseMappingStr(axesStr);
	}

	private List<Integer> readIntArray(JsonReader reader) throws IOException {
		List<Integer> res = new ArrayList<>();
		try {
			Integer singleEntry = reader.nextInt();
			res.add(singleEntry);
		}
		catch(IllegalStateException | NumberFormatException e) {
			reader.beginArray();
			while (reader.hasNext()) {
				res.add(reader.nextInt());
			}
			reader.endArray();
		}
		return res;
	}

	private List<Boolean> readBooleanArray(JsonReader reader) throws IOException {
		List<Boolean> res = new ArrayList<>();
		try {
			Boolean singleEntry = reader.nextBoolean();
			res.add(singleEntry);
		}
		catch(IllegalStateException | NumberFormatException e) {
			reader.beginArray();
			while (reader.hasNext()) {
				res.add(reader.nextBoolean());
			}
			reader.endArray();
		}
		return res;
	}

	protected void setModel(final SavedModelBundle model) {
		this.model = model;
	}

	@Override
	public void preprocess() {
		initMapping();
		calculateMapping();
	}

	@Override
	public void initMapping() {
		inputNode.setMappingDefaults();
		outputNode.setMappingDefaults();
	}

	@Override
	public void calculateMapping() {
		doDimensionReduction();
		generateMapping();
	}

	@Override
	public void doDimensionReduction() {
		int diff = getOutputNode().getNodeShape().length - getInputNode().getNodeShape().length;
		if(diff == 0) return;
		if(diff > 0) status.logError("Cannot handle case INPUT TENSOR SIZE < OUTPUT TENSOR SIZE");
		if(diff < -1) status.logError("OUTPUT TENSOR SIZE can only be one dimension smaller than INPUT TENSOR SIZE");
		isDoingDimensionReduction = true;
		if(getInputNode().getImageAxes().contains(Axes.TIME)) {
			axisToRemove = Axes.TIME;
		} else {
			axisToRemove = Axes.Z;
		}
		handleDimensionReduction();
	}

	@Override
	public boolean libraryLoaded() {
		return foundJNI;
	}

	@Override
	public boolean supportsGPU() {
		return gpuSupport;
	}

	private void generateMapping() {
		inputNode.generateMapping();
		outputNode.generateMapping();
	}

	private void handleDimensionReduction() {
		if (isDoingDimensionReduction) {
			final Dataset outputDummy = createEmptyDuplicateWithoutAxis(inputNode
				.getImageAxes(), inputNode.getImageDimensions(), axisToRemove);
			getOutputNode().initialize(outputDummy);
			List<AxisType> mapping = new ArrayList<>();
			mapping.addAll(getInputNode().getNodeAxes());
			mapping.remove(axisToRemove);
			getOutputNode().setMapping(mapping.toArray(new AxisType[0]));
//			getOutputNode().removeAxisFromMapping(axisToRemove);
		}
		else {
			getOutputNode().initialize(inputNode.getImage());
			getOutputNode().setMapping(getInputNode().getMapping());
		}
	}

	private Dataset createEmptyDuplicateWithoutAxis(List<AxisType> imageAxes, List<Long> imageDimensions, AxisType axisToRemove)
	{
		int numDims = imageAxes.size();
		if (imageAxes.contains(axisToRemove)) {
			numDims--;
		}
		final long[] dims = new long[numDims];
		final AxisType[] axes = new AxisType[numDims];
		int j = 0;
		for (int i = 0; i < numDims; i++) {
			final AxisType axisType = imageAxes.get(i);
			if (axisType != axisToRemove) {
				axes[j] = axisType;
				dims[j] = imageDimensions.get(i);
				j++;
			}
		}
		final Dataset result = datasetService.create(new FloatType(), dims, "",
			axes);
		return result;
	}

	// TODO this is the tensorflow runner
	@Override
	public RandomAccessibleInterval<T> execute(
		final RandomAccessibleInterval<T> tile) throws IllegalArgumentException, OutOfMemoryError, ExecutionException {

		final Tensor inputTensor = DatasetTensorFlowConverter.datasetToTensor(tile,
			getInputNode().getMappingIndices());
		if (inputTensor != null) {
			RandomAccessibleInterval<T> output = null;
			Tensor outputTensor = TensorFlowRunner.executeGraph(model, inputTensor,
				getInputTensorInfo(), getOutputTensorInfo());

			if (outputTensor != null) {
				output = DatasetTensorFlowConverter.tensorToDataset(outputTensor, tile
					.randomAccess().get(), getOutputNode().getMappingIndices(),
					dropSingletonDims);
				outputTensor.close();
			}
			inputTensor.close();
			return output;
		}
		return null;
	}

	@Override
	public boolean isInitialized() {
		return model != null;
	}

	public void setInputTensor(final TensorInfo tensorInfo) {
		inputTensorInfo = tensorInfo;
		logTensorShape("Shape of input tensor", tensorInfo);
	}

	protected void logTensorShape(String title, final TensorInfo tensorInfo) {
		long[] dims = new long[tensorInfo.getTensorShape().getDimCount()];
		for (int i = 0; i < dims.length; i++) {
			dims[i] = tensorInfo.getTensorShape().getDimList().get(i).getSize();
		}
		log(title + ": " + Arrays.toString(dims));
	}

	public void setOutputTensor(final TensorInfo tensorInfo) {
		outputTensorInfo = tensorInfo;
		logTensorShape("Shape of output tensor", tensorInfo);
	}

	public TensorInfo getInputTensorInfo() {
		return inputTensorInfo;
	}

	public TensorInfo getOutputTensorInfo() {
		return outputTensorInfo;
	}

	@Override
	public void clear() {
		super.clear();
		sig = null;
		model = null;
		inputTensorInfo = null;
		outputTensorInfo = null;
		isDoingDimensionReduction = false;
		axisToRemove = null;
	}

	@Override
	public void dispose() {
		super.dispose();
		foundJNI = true;
		gpuSupport = false;
		clear();
	}

}
