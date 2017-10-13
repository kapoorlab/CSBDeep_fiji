package mpicbg.csbd.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.tensorflow.Graph;
import org.tensorflow.Tensor;

import mpicbg.csbd.imglib2.ArrangedView;
import mpicbg.csbd.imglib2.CombinedView;
import mpicbg.csbd.imglib2.TiledView;
import mpicbg.csbd.normalize.Normalizer;
import mpicbg.csbd.tensorflow.DatasetConverter;
import mpicbg.csbd.tensorflow.DatasetTensorBridge;
import mpicbg.csbd.tensorflow.TensorFlowRunner;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class TiledPredictionUtil {

	public static <T extends RealType<T>> RandomAccessibleInterval<FloatType> tiledPrediction(
			final RandomAccessibleInterval<T> input,
			final int nTiles,
			final int blockMultiple,
			final int overlap,
			final DatasetConverter<T> datasetConverter,
			final DatasetTensorBridge bridge,
			final Normalizer normalizer,
			final Graph graph,
			final String inputNodeName,
			final String outputNodeName) { // TODO output type
		// Get the dimensions of the image
		long[] shape = new long[input.numDimensions()];
		input.dimensions(shape);

		// Get the largest dimension and its size
		int largestDim = 0;
		long largestSize = 0;
		for (int d = 0; d < input.numDimensions(); d++) {
			if (shape[d] > largestSize) {
				largestSize = shape[d];
				largestDim = d;
			}
		}

		// Calculate the blocksize to use
		double blockwidthIdeal = largestSize / (double) nTiles;
		long blockwidth = (long) (Math.ceil(blockwidthIdeal / blockMultiple) * blockMultiple);
		long[] blockSize = Arrays.copyOf(shape, input.numDimensions());
		blockSize[largestDim] = blockwidth;

		// Expand the image to fit the blocksize
		RandomAccessibleInterval<T> im = expandDimToSize(input, largestDim, blockwidth * nTiles);
		printDim("After expand", im);

		// Put the padding per dimension in a array
		long[] padding = new long[im.numDimensions()];
		padding[largestDim] = overlap;

		// Create the tiled view
		TiledView<T> tiledView = new TiledView<>(im, blockSize, padding);
		Cursor<RandomAccessibleInterval<T>> cursor = Views.iterable(tiledView).cursor();

		// Set padding to negative to remove it later
		long[] negPadding = padding.clone();
		negPadding[largestDim] = - padding[largestDim];

		// Loop over the tiles and execute the prediction
		List<RandomAccessibleInterval<FloatType>> results = new ArrayList<>();
		while (cursor.hasNext()) {
			RandomAccessibleInterval<T> tile = cursor.next();
			//uiService.show(tile);
			RandomAccessibleInterval<FloatType> tileExecuted = executeGraphWithPadding(tile,
					datasetConverter, bridge, normalizer, graph, inputNodeName, outputNodeName);
			// Remove padding
			tileExecuted = Views.zeroMin(Views.expandZero(tileExecuted, negPadding));
			//uiService.show(tileExecuted);
			results.add(tileExecuted);
		}
		// Arrange and combine the tiles again
		long[] grid = new long[results.get(0).numDimensions()];
		for (int i = 0; i < grid.length; i++) {
			grid[i] = i == largestDim ? nTiles : 1;
		}
		RandomAccessibleInterval<FloatType> result = new CombinedView<FloatType>(new ArrangedView<>(results, grid));
		return expandDimToSize(result, largestDim, shape[largestDim]);
	}
	
	private static <T extends RealType<T>> RandomAccessibleInterval<T> expandDimToSize(RandomAccessibleInterval<T> im, int d, long size) {
		final int n = im.numDimensions();
		final long[] min = new long[ n ];
		final long[] max = new long[ n ];
		im.min( min );
		im.max( max );
		max[ d ] += (size - im.dimension(d));
		return Views.interval(Views.extendMirrorDouble(im), new FinalInterval(min, max));
	}

	private static <T extends RealType<T>> RandomAccessibleInterval<FloatType> executeGraphWithPadding(
			final RandomAccessibleInterval<T> input,
			final DatasetConverter<T> datasetConverter,
			final DatasetTensorBridge bridge,
			final Normalizer normalizer,
			final Graph graph,
			final String inputNodeName,
			final String outputNodeName) {
		Tensor inputTensor = datasetConverter.datasetToTensor(input, bridge, normalizer);
		Tensor outputTensor = TensorFlowRunner.executeGraph(
													graph,
													inputTensor,
													inputNodeName,
													outputNodeName );
		return datasetConverter.tensorToDataset(outputTensor, bridge);
	}

	// TODO remove
	private static void printDim(String name, RandomAccessibleInterval<?> im) {
		System.out.print(name + ": [ ");
		for (int i = 0; i < im.numDimensions(); i++) {
			System.out.print(im.dimension(i) + " ");
		}
		System.out.println("]");
	}

}