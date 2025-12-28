/*
 * Copyright (c) 2018 Villu Ruusmann
 *
 * This file is part of JPMML-R
 *
 * JPMML-R is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-R is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-R.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.rexp;

import java.util.ArrayList;
import java.util.List;

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.neural_network.NeuralEntity;
import org.dmg.pmml.neural_network.NeuralInputs;
import org.dmg.pmml.neural_network.NeuralLayer;
import org.dmg.pmml.neural_network.NeuralNetwork;
import org.dmg.pmml.neural_network.Neuron;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.neural_network.NeuralNetworkUtil;

public class NNConverter extends ModelConverter<RGenericVector> {

	public NNConverter(RGenericVector nn){
		super(nn);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector nn = getObject();

		RGenericVector modelList = nn.getGenericElement("model.list");

		RStringVector response = modelList.getStringElement("response");
		RStringVector variables = modelList.getStringElement("variables");

		{
			DataField dataField = encoder.createDataField(response.asScalar(), OpType.CONTINUOUS, DataType.DOUBLE);

			encoder.setLabel(dataField);
		}

		for(int i = 0; i < variables.size(); i++){
			String variable = variables.getValue(i);

			DataField dataField = encoder.createDataField(variable, OpType.CONTINUOUS, DataType.DOUBLE);

			encoder.addFeature(dataField);
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector nn = getObject();

		RExp actFct = nn.getElement("act.fct");
		RBooleanVector linearOutput = nn.getBooleanElement("linear.output");
		RGenericVector weights = nn.getGenericElement("weights");

		RStringVector actFctType = actFct.getStringAttribute("type");

		// Select the first repetition
		weights = weights.getGenericValue(0);

		NeuralNetwork.ActivationFunction activationFunction = parseActivationFunction(actFctType.asScalar());

		ContinuousLabel continuousLabel = schema.requireContinuousLabel();
		List<? extends Feature> features = schema.getFeatures();

		NeuralInputs neuralInputs = NeuralNetworkUtil.createNeuralInputs(features, DataType.DOUBLE);

		List<NeuralLayer> neuralLayers = new ArrayList<>();

		List<? extends NeuralEntity> entities = neuralInputs.getNeuralInputs();

		for(int i = 0; i < weights.size(); i++){
			boolean hidden = (i < (weights.size() - 1));

			NeuralLayer neuralLayer = new NeuralLayer();

			if(hidden || (linearOutput != null && !linearOutput.asScalar())){
				neuralLayer.setActivationFunction(activationFunction);
			}

			RDoubleVector layerWeights = weights.getDoubleValue(i);

			RIntegerVector layerDim = layerWeights.dim();

			int layerRows = layerDim.getValue(0);
			int layerColumns = layerDim.getValue(1);

			for(int j = 0; j < layerColumns; j++){
				List<Double> neuronWeights = FortranMatrixUtil.getColumn(layerWeights.getValues(), layerRows, layerColumns, j);

				String id;

				if(hidden){
					id = "hidden/" + String.valueOf(i) + "/" + String.valueOf(j);
				} else

				{
					id = "output/" + String.valueOf(j);
				}

				Neuron neuron = NeuralNetworkUtil.createNeuron(entities, neuronWeights.subList(1, neuronWeights.size()), neuronWeights.get(0))
					.setId(id);

				neuralLayer.addNeurons(neuron);
			}

			neuralLayers.add(neuralLayer);

			entities = neuralLayer.getNeurons();
		}

		NeuralNetwork neuralNetwork = new NeuralNetwork(MiningFunction.REGRESSION, NeuralNetwork.ActivationFunction.IDENTITY, ModelUtil.createMiningSchema(continuousLabel), neuralInputs, neuralLayers)
			.setNeuralOutputs(NeuralNetworkUtil.createRegressionNeuralOutputs(entities, continuousLabel));

		return neuralNetwork;
	}

	static
	private NeuralNetwork.ActivationFunction parseActivationFunction(String actFctType){

		switch(actFctType){
			case "logistic":
				return NeuralNetwork.ActivationFunction.LOGISTIC;
			case "tanh":
				return NeuralNetwork.ActivationFunction.TANH;
			default:
				throw new RExpException("Activation function \'" + actFctType + "\' is not supported");
		}
	}
}