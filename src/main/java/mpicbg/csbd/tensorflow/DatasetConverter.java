package mpicbg.csbd.tensorflow;

import org.tensorflow.Tensor;

import mpicbg.csbd.normalize.Normalizer;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public interface DatasetConverter<T extends RealType<T>>{

	RandomAccessibleInterval<FloatType> tensorToDataset( Tensor tensor, DatasetTensorBridge bridge );

	Tensor datasetToTensor( RandomAccessibleInterval<T> image, DatasetTensorBridge bridge, Normalizer normalizer );

}