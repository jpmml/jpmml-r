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

import com.google.common.collect.Iterables;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Entity;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.neural_network.Connection;
import org.dmg.pmml.neural_network.NeuralInputs;
import org.dmg.pmml.neural_network.NeuralLayer;
import org.dmg.pmml.neural_network.NeuralNetwork;
import org.dmg.pmml.neural_network.Neuron;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.Label;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.neural_network.NeuralNetworkUtil;

public class NNetConverter extends ModelConverter<RGenericVector> {

	public NNetConverter(RGenericVector nnet){
		super(nnet);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector nnet = getObject();

		RStringVector lev = (RStringVector)nnet.getValue("lev", true);
		RExp terms = nnet.getValue("terms");
		RGenericVector xlevels = (RGenericVector)nnet.getValue("xlevels");
		RStringVector coefnames = (RStringVector)nnet.getValue("coefnames");

		RIntegerVector response = (RIntegerVector)terms.getAttributeValue("response");

		FormulaContext context = new XLevelsFormulaContext(xlevels);

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		// Dependent variable
		int responseIndex = response.asScalar();
		if(responseIndex != 0){
			DataField dataField = (DataField)formula.getField(responseIndex - 1);

			if(lev != null){
				dataField = (DataField)encoder.toCategorical(dataField.getName(), lev.getValues());
			}

			encoder.setLabel(dataField);
		} else

		{
			throw new IllegalArgumentException();
		}

		// Independent variables
		for(int i = 0; i < coefnames.size(); i++){
			Feature feature = formula.resolveFeature(coefnames.getValue(i));

			encoder.addFeature(feature);
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector nnet = getObject();

		RDoubleVector n = (RDoubleVector)nnet.getValue("n");
		RBooleanVector linout = (RBooleanVector)nnet.getValue("linout", true);
		RBooleanVector softmax = (RBooleanVector)nnet.getValue("softmax", true);
		RBooleanVector censored = (RBooleanVector)nnet.getValue("censored", true);
		RDoubleVector wts = (RDoubleVector)nnet.getValue("wts");
		RStringVector lev = (RStringVector)nnet.getValue("lev", true);

		if(n.size() != 3){
			throw new IllegalArgumentException();
		}

		Label label = schema.getLabel();
		List<? extends Feature> features = schema.getFeatures();

		MiningFunction miningFunction;

		if(lev == null){

			if(linout != null && !linout.asScalar()){
				throw new IllegalArgumentException();
			}

			miningFunction = MiningFunction.REGRESSION;
		} else

		{
			miningFunction = MiningFunction.CLASSIFICATION;
		}

		int nInput = ValueUtil.asInt(n.getValue(0));
		if(nInput != features.size()){
			throw new IllegalArgumentException();
		}

		NeuralInputs neuralInputs = NeuralNetworkUtil.createNeuralInputs(features, DataType.DOUBLE);

		int offset = 0;

		List<NeuralLayer> neuralLayers = new ArrayList<>();

		List<? extends Entity> entities = neuralInputs.getNeuralInputs();

		int nHidden = ValueUtil.asInt(n.getValue(1));
		if(nHidden > 0){
			NeuralLayer neuralLayer = encodeNeuralLayer("hidden", nHidden, entities, wts, offset)
				.setActivationFunction(NeuralNetwork.ActivationFunction.LOGISTIC);

			offset += (nHidden * (entities.size() + 1));

			neuralLayers.add(neuralLayer);

			entities = neuralLayer.getNeurons();
		}

		int nOutput = ValueUtil.asInt(n.getValue(2));
		if(nOutput == 1){
			NeuralLayer neuralLayer = encodeNeuralLayer("output", nOutput, entities, wts, offset);

			offset += (nOutput * (entities.size() + 1));

			neuralLayers.add(neuralLayer);

			switch(miningFunction){
				case REGRESSION:
					break;
				case CLASSIFICATION:
					{
						neuralLayer = encodeLogisticTransform(getOnlyNeuron(neuralLayer));

						neuralLayers.add(neuralLayer);

						neuralLayer = encodeLabelBinarizerTransform(getOnlyNeuron(neuralLayer));

						neuralLayers.add(neuralLayer);
					}
					break;
			}

			entities = neuralLayer.getNeurons();
		} else

		if(nOutput > 1){
			NeuralLayer neuralLayer = encodeNeuralLayer("output", nOutput, entities, wts, offset);

			if(softmax != null && softmax.asScalar()){

				if(censored != null && censored.asScalar()){
					throw new IllegalArgumentException();
				}

				neuralLayer.setNormalizationMethod(NeuralNetwork.NormalizationMethod.SOFTMAX);
			}

			offset += (nOutput * (entities.size() + 1));

			neuralLayers.add(neuralLayer);

			entities = neuralLayer.getNeurons();
		} else

		{
			throw new IllegalArgumentException();
		}

		NeuralNetwork neuralNetwork = new NeuralNetwork(miningFunction, NeuralNetwork.ActivationFunction.IDENTITY, ModelUtil.createMiningSchema(label), neuralInputs, neuralLayers);

		switch(miningFunction){
			case REGRESSION:
				neuralNetwork
					.setNeuralOutputs(NeuralNetworkUtil.createRegressionNeuralOutputs(entities, (ContinuousLabel)label));
				break;
			case CLASSIFICATION:
				neuralNetwork
					.setNeuralOutputs(NeuralNetworkUtil.createClassificationNeuralOutputs(entities, (CategoricalLabel)label))
					.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, (CategoricalLabel)label));
				break;
		}

		return neuralNetwork;
	}

	static
	private NeuralLayer encodeNeuralLayer(String prefix, int n, List<? extends Entity> entities, RDoubleVector wts, int offset){
		NeuralLayer neuralLayer = new NeuralLayer();

		for(int i = 0; i < n; i++){
			List<Double> weights = (wts.getValues()).subList(offset + 1, offset + (entities.size() + 1));
			Double bias = wts.getValue(offset);

			Neuron neuron = NeuralNetworkUtil.createNeuron(entities, weights, bias)
				.setId(prefix + "/" + String.valueOf(i + 1));

			neuralLayer.addNeurons(neuron);

			offset += (entities.size() + 1);
		}

		return neuralLayer;
	}

	static
	private NeuralLayer encodeLogisticTransform(Entity entity){
		NeuralLayer neuralLayer = new NeuralLayer()
			.setActivationFunction(NeuralNetwork.ActivationFunction.LOGISTIC);

		Neuron neuron = new Neuron()
			.setId("logistic/1")
			.setBias(0d)
			.addConnections(new Connection(entity.getId(), 1d));

		neuralLayer.addNeurons(neuron);

		return neuralLayer;
	}

	static
	private NeuralLayer encodeLabelBinarizerTransform(Entity entity){
		NeuralLayer neuralLayer = new NeuralLayer();

		Neuron noEventNeuron = new Neuron()
			.setId("event/false")
			.setBias(1d)
			.addConnections(new Connection(entity.getId(), -1d));

		Neuron eventNeuron = new Neuron()
			.setId("event/true")
			.setBias(0d)
			.addConnections(new Connection(entity.getId(), 1d));

		neuralLayer.addNeurons(noEventNeuron, eventNeuron);

		return neuralLayer;
	}

	static
	private Neuron getOnlyNeuron(NeuralLayer neuralLayer){
		List<Neuron> neurons = neuralLayer.getNeurons();

		return Iterables.getOnlyElement(neurons);
	}
}