/*
 * Copyright (c) 2014 Villu Ruusmann
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
package org.jpmml.rexp.example;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import org.dmg.pmml.PMML;
import org.jpmml.model.JAXBSerializer;
import org.jpmml.model.metro.MetroJAXBSerializer;
import org.jpmml.rexp.Converter;
import org.jpmml.rexp.ConverterFactory;
import org.jpmml.rexp.RExp;
import org.jpmml.rexp.RExpParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

	@Parameter (
		names = "--converter",
		description = "Converter class"
	)
	private String converter = null;

	@Parameter (
		names = "--help",
		description = "Show the list of configuration options and exit",
		help = true
	)
	private boolean help = false;

	@Parameter (
		names = {"--model-rds-input", "--rds-input"},
		description = "RDS input file",
		required = true
	)
	private File input = null;

	@Parameter (
		names = "--pmml-output",
		description = "PMML output file",
		required = true
	)
	private File output = null;


	static
	public void main(String... args) throws Exception {
		Main main = new Main();

		JCommander commander = new JCommander(main);
		commander.setProgramName(Main.class.getName());

		try {
			commander.parse(args);
		} catch(ParameterException pe){
			StringBuilder sb = new StringBuilder();

			sb.append(pe.toString());
			sb.append("\n");

			commander.usage(sb);

			System.err.println(sb.toString());

			System.exit(-1);
		}

		if(main.help){
			StringBuilder sb = new StringBuilder();

			commander.usage(sb);

			System.out.println(sb.toString());

			System.exit(0);
		}

		main.run();
	}

	public void run() throws Exception {
		RExp rexp;

		try(InputStream is = new FileInputStream(this.input)){
			logger.info("Parsing RDS..");

			RExpParser parser = new RExpParser(is);

			long begin = System.currentTimeMillis();
			rexp = parser.parse();
			long end = System.currentTimeMillis();

			logger.info("Parsed RDS in {} ms.", (end - begin));
		} catch(Exception e){
			logger.error("Failed to parse RDS", e);

			throw e;
		}

		ConverterFactory converterFactory = ConverterFactory.newInstance();

		Converter<RExp> converter;

		if(this.converter != null){
			logger.info("Initializing user-specified Converter {}", this.converter);

			Class<? extends Converter<?>> clazz = (Class<? extends Converter<?>>)Class.forName(this.converter);

			converter = converterFactory.newConverter(clazz, rexp);
		} else

		{
			logger.info("Initializing default Converter");

			converter = converterFactory.newConverter(rexp);
		} // End if

		{
			Class<?> clazz = converter.getClass();

			logger.info("Initialized {}", clazz.getName());
		}

		PMML pmml;

		try {
			logger.info("Converting RDS to PMML..");

			long begin = System.currentTimeMillis();
			pmml = converter.encodePMML();
			long end = System.currentTimeMillis();

			logger.info("Converted RDS to PMML in {} ms.", (end - begin));
		} catch(Exception e){
			logger.error("Failed to convert RDS to PMML", e);

			throw e;
		} // End try

		try(OutputStream os = new FileOutputStream(this.output)){
			logger.info("Marshalling PMML..");

			JAXBSerializer jaxbSerializer = new MetroJAXBSerializer();

			long begin = System.currentTimeMillis();
			jaxbSerializer.serializePretty(pmml, os);
			long end = System.currentTimeMillis();

			logger.info("Marshalled PMML in {} ms.", (end - begin));
		} catch(Exception e){
			logger.error("Failed to marshal PMML", e);

			throw e;
		}
	}

	public String getConverter(){
		return this.converter;
	}

	public void setConverter(String converter){
		this.converter = converter;
	}

	public File getInput(){
		return this.input;
	}

	public void setInput(File input){

		if(input == null){
			throw new NullPointerException();
		}

		this.input = input;
	}

	public File getOutput(){
		return this.output;
	}

	public void setOutput(File output){

		if(output == null){
			throw new NullPointerException();
		}

		this.output = output;
	}

	private static final Logger logger = LoggerFactory.getLogger(Main.class);
}