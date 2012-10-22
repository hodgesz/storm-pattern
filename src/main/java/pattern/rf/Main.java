/*
 * Copyright (c) 2007-2012 Concurrent, Inc. All Rights Reserved.
 *
 * Project and contact information: http://www.cascading.org/
 *
 * This file is part of the Cascading project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pattern.rf;

import cascading.flow.Flow;
import cascading.flow.FlowDef;
import cascading.flow.hadoop.HadoopFlowConnector;
import cascading.operation.aggregator.Count;
import cascading.pipe.Each;
import cascading.pipe.Every;
import cascading.pipe.GroupBy;
import cascading.pipe.Pipe;
import cascading.pipe.assembly.Rename;
import cascading.property.AppProps;
import cascading.scheme.Scheme;
import cascading.scheme.hadoop.TextDelimited;
import cascading.tap.Tap;
import cascading.tap.hadoop.Hfs;
import cascading.tuple.Fields;
import java.util.Properties;


public class
  Main
  {
  public static void
  main( String[] args )
    {
    String pmmlPath = args[ 0 ];
    String ordersPath = args[ 1 ];
    String classifyPath = args[ 2 ];
    String confusePath = args[ 3 ];

    Properties properties = new Properties();
    AppProps.setApplicationJarClass( properties, Main.class );
    HadoopFlowConnector flowConnector = new HadoopFlowConnector( properties );

    // build the RF classifier model from PMML
    RandomForest rf = null;

    try {
      rf = new RandomForest( pmmlPath );
    } catch ( Exception e ) {
      e.printStackTrace();
      System.exit( -1 );
    }

    // create source and sink taps
    Tap ordersTap = new Hfs( new TextDelimited( true, "\t" ), ordersPath );
    Tap classifyTap = new Hfs( new TextDelimited( true, "\t" ), classifyPath );
    Tap confuseTap = new Hfs( new TextDelimited( true, "\t" ), confusePath );

    // define "Classifier" to evaluate the orders
    Pipe classifyPipe = new Pipe( "classify" );
    classifyPipe = new Each( classifyPipe, Fields.ALL, new Classifier( new Fields( "score" ), rf ), Fields.ALL );
    classifyPipe = new Rename( classifyPipe, new Fields( 0 ), new Fields( "label" ) );

    // calculate a confusion matrix for the model results
    Pipe confusePipe = new Pipe( "confuse", classifyPipe );
    confusePipe = new GroupBy( confusePipe, new Fields( "score", "label" ) );
    confusePipe = new Every( confusePipe, Fields.ALL, new Count(), Fields.ALL );

    // connect the taps, pipes, etc., into a flow
    FlowDef flowDef = FlowDef.flowDef()
     .setName( "classify" )
     .addSource( classifyPipe, ordersTap )
     .addSink( classifyPipe, classifyTap )
     .addTailSink( confusePipe, confuseTap );

    // write a DOT file and run the flow
    Flow classifyFlow = flowConnector.connect( flowDef );
    classifyFlow.writeDOT( "dot/classify.dot" );
    classifyFlow.complete();
    }
  }