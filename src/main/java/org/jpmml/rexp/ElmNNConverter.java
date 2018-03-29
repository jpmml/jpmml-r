/*
 * Copyright (c) 2017 Villu Ruusmann
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
import org.dmg.pmml.Entity;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.neural_network.NeuralInputs;
import org.dmg.pmml.neural_network.NeuralLayer;
import org.dmg.pmml.neural_network.NeuralNetwork;
import org.dmg.pmml.neural_network.NeuralOutputs;
import org.dmg.pmml.neural_network.Neuron;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.Label;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.neural_network.NeuralNetworkUtil;

public class ElmNNConverter extends ModelConverter<RGenericVector> {

	public ElmNNConverter(RGenericVector elmNN){
		super(elmNN);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector elmNN = getObject();

		final
		RGenericVector model;

		try {
			model = (RGenericVector)elmNN.getValue("model");
		} catch(IllegalArgumentException iae){
			throw new IllegalArgumentException("No model frame information. Please initialize the \'model\' element", iae);
		}

		RExp terms = model.getAttributeValue("terms");

		RIntegerVector response = (RIntegerVector)terms.getAttributeValue("response");
		RStringVector columns = (RStringVector)terms.getAttributeValue("columns");

		FormulaContext context = new ModelFrameFormulaContext(model);

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		// Dependent variable
		int responseIndex = response.asScalar();
		if(responseIndex != 0){
			DataField dataField = (DataField)formula.getField(responseIndex - 1);

			encoder.setLabel(dataField);
		}

		// Independent variables
		for(int i = 0; i < columns.size(); i++){
			String column = columns.getValue(i);

			if(i == 0 && "(Intercept)".equals(column)){
				continue;
			}

			Feature feature = formula.resolveFeature(column);

			encoder.addFeature(feature);
		}
	}

	@Override
	public NeuralNetwork encodeModel(Schema schema){
		RGenericVector elmNN = getObject();

		RDoubleVector inpweight = (RDoubleVector)elmNN.getValue("inpweight");
		RDoubleVector biashid = (RDoubleVector)elmNN.getValue("biashid");
		RDoubleVector outweight = (RDoubleVector)elmNN.getValue("outweight");
		RStringVector actfun = (RStringVector)elmNN.getValue("actfun");
		RDoubleVector nhid = (RDoubleVector)elmNN.getValue("nhid");

		Label label = schema.getLabel();
		List<? extends Feature> features = schema.getFeatures();

		switch(actfun.asScalar()){
			case "purelin":
				break;
			default:
				throw new IllegalArgumentException();
		}

		NeuralInputs neuralInputs = NeuralNetworkUtil.createNeuralInputs(features, DataType.DOUBLE);

		List<? extends Entity> entities = neuralInputs.getNeuralInputs();

		List<NeuralLayer> neuralLayers = new ArrayList<>(2);

		NeuralLayer hiddenNeuralLayer = new NeuralLayer();

		int rows = ValueUtil.asInt(nhid.asScalar());
		int columns = 1 + features.size();

		for(int row = 0; row < rows; row++){
			List<Double> weights = FortranMatrixUtil.getRow(inpweight.getValues(), rows, columns, row);
			Double bias = biashid.getValue(row);

			bias += weights.remove(0);

			Neuron neuron = NeuralNetworkUtil.createNeuron(entities, weights, bias)
				.setId("hidden/" + String.valueOf(row + 1));

			hiddenNeuralLayer.addNeurons(neuron);
		}

		neuralLayers.add(hiddenNeuralLayer);

		entities = hiddenNeuralLayer.getNeurons();

		NeuralLayer outputNeuralLayer = new NeuralLayer();

		// XXX
		columns = 1;

		for(int column = 0; column < columns; column++){
			List<Double> weights = FortranMatrixUtil.getColumn(outweight.getValues(), rows, columns, column);
			Double bias = Double.NaN;

			Neuron neuron = NeuralNetworkUtil.createNeuron(entities, weights, bias)
				.setId("output/" + String.valueOf(column + 1));

			outputNeuralLayer.addNeurons(neuron);
		}

		neuralLayers.add(outputNeuralLayer);

		entities = outputNeuralLayer.getNeurons();

		NeuralOutputs neuralOutputs = NeuralNetworkUtil.createRegressionNeuralOutputs(entities, (ContinuousLabel)label);

		NeuralNetwork neuralNetwork = new NeuralNetwork(MiningFunction.REGRESSION, NeuralNetwork.ActivationFunction.IDENTITY, ModelUtil.createMiningSchema(label), neuralInputs, neuralLayers)
			.setNeuralOutputs(neuralOutputs);

		return neuralNetwork;
	}
}