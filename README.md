Groovy DSL For Spring Integration
=================================

#Overview

This project implements a Groovy DSL for Spring Integration. Coming on the heels of the [Scala DSL for Spring Integration] (https://github.com/SpringSource/spring-integration-scala), I have incorporated some of the same basic ideas and vocabulary however this is intended for Groovy developers and Java developers looking for a simple and powerful alternative to XML configuration. 

## Features
* Simple configuration using the Groovy builder pattern with the ability to implement endpoint logic in closures
* messageFlow automatically chains endpoints, eliminating the need to declare channels
* Named channels are supported if needed
* Supports native Spring Integration XML configuration including all XML attributes, configuration of Spring Integration components not directly implemented in the DSL, and configuration of plain old Spring beans
* Builds a Spring Application context which may be accessed if necessary
* May be executed in Groovy or from a Java class

## Implementation

The DSL uses Groovy Builder pattern so the syntax will be familiar to Groovyists. The IntegrationBuilder uses the [FactoryBuilderSupport](http://groovy.codehaus.org/FactoryBuilderSupport) framework to create a Spring Integration domain model which is translated directly to Spring XML to create an XMLApplicationContext via the builder's createApplicationContext() method. The main benefit of delegating to an XMLApplicationContext is that Spring Integration already has implemented many namespace parsers that perform all the necessary validation and bean creation. This code is tightly coupled to XML bean definitions and would otherwise need to be entirely replicated for the DSL. Another advantage is that if logging is set to DEBUG, you can see the generated XML on the console which will give you more insight into how the DSL is interpretting things. Finally it makes it very easy for the IntegrationBuilder to inject XML builder markup, providing maximum flexibility.

# Examples

## Message Flows

The MessageFlow is a container that acts like a Spring Integration 'chain' element on steroids. Child components are Spring Integration endpoints that are executed in sequence, connected with automatically created channels. Channel names may be provided and/or alternate channel definitions may provided as needed. 
 
The default argument (value) of a messageFlow is its name and its input channel becomes "${name}.inputChannel". These are automatically generated by default.
A messageFlow may also specify an outputChannel. It also provides convenience methods send(obj), sendAndReceive(obj) to directly invoke a message flow. These methods may accept a Spring Integration Message, in which case sendAndReceive will also return a Message, or any object which will be used as the Message payload.

The following is a simple example using Groovy.

    def builder = new IntegrationBuilder()

    def flow = builder.messageFlow {
	 transform(evaluate:{payload->payload.toUpperCase()})
	 filter(evaluate:{payload-> payload =="HELLO"})
	 handle(evaluate:{payload->payload})
    }

    assert flow.sendAndReceive("hello") == "HELLO"
    assert flow.sendAndReceive("world") == null

The above example illustrates the basic usage. Use the IntegrationBuilder() to build a MessageFlow and get a reference to it. There are also ways to obtain flow instances via the IntegrationBuilder, e.g. builder.messageFlows[0]. Not that the endpoints are implicitly linked and that each is backed by a closure. The closure is a named attribute: 'evaluate' ('action' also works if you like). This is done this way so as not to confuse the builder which otherwise would try to consume the closure itself. The 'transform' and 'filter' method create a Transformer and Filter respectively, 'handle' corresponds to a Service Activator. 

Note that the 'evaluate' closure may accept a Message, Message Headers, or Payload. This is accomplished by declaring the closure parameter type. if Message, the message will be passed, if a Map, the parameter will be the Message headers unless the payload itself is a Map, then the payload will be used. It's the payload by default.

This flow can also be executed from a Java class. The easiest way is to create an external file or resource, or anything that provides an InputStream.  This resource contains:
     messageFlow {
	 transform(evaluate:{payload->payload.toUpperCase()})
	 filter(evaluate:{payload-> payload =="HELLO"})
	 handle(evaluate:{payload->payload})
     }

The Equivalant Java code is:
    IntegrationBuilder builder = new IntegrationBuilder();
    MessageFlow flow = (MessageFlow)builder.build(new FileInputStream("messageFlow1.groovy");
    flow.sendAndReceive("hello");
  
Multiple Message Flows:

      def flow1 = builder.messageFlow('flow1',outputChannel:'outputChannel1') {
        transform(evaluate:{it.toUpperCase()})
      }
      def flow2 = builder.messageFlow('flow2',inputChannel:'outputChannel1') {
	filter(evaluate:{it.class == String})
	transform(evaluate:{it.toLowerCase()})
      }
		
      assert flow1.sendAndReceive("hello") == "hello"
		
      def response = builder.integrationContext.sendAndReceive(flow1.inputChannel, "hElLo")
      assert response == "hello", response


The above example illustrates two message flows explicitly connected through a named channel 'outputChannel1'. A message sent to flow1's input channel is routed 
to flow2 via its outputChannel. Note that the name of the flow1 input channel is automatically created by appending '.inputChannel' to it's name. This is true of all endpoints.

# Building an IntegrationContext with 'doWithSpringIntegration'

## Multiple MessageFlows
It is possible to build multiple MessageFlows. In Groovy, builder.messageFlow() may be invoked multiple times. Java access requires a single root 'doWithSpringIntegration', also useful in Groovy code 

      doWithSpringIntegration {
	 messageFlow(outputChannel:'outputChannel1') {
		transform(evaluate:{it.toUpperCase()})
	 }
		
	 def flow2 = messageFlow(inputChannel:'outputChannel1') {
	        transform(evaluate:{it.toLowerCase()})
	 }
 		
	 handle(inputChannel:flow2.outputChannel,evaluate:{println it})
       }

doWithSpringIntegration returns an IntegrationContext which can be used to access MessageFlows (they are returned as a List). IntegrationContext also provides 
send() and sendAndReceive() which require an inputChannel as well as a Message or payload

    def integrationContext = doWithSpringIntegration {builder->
   		
   		def flow1 = builder.messageFlow(outputChannel:'outputChannel1') {
			transform(evaluate:{it.toUpperCase()})
		}
		
		def flow2 = builder.messageFlow(inputChannel:'outputChannel1') {
			transform(evaluate:{it.toLowerCase()})
		}
		
		handle(inputChannel:flow2.outputChannel,evaluate:{println it})
    }

## SubFlows
The following example illustrates the use of the exec() method to execute a flow within a flow
        
        doWithSpringIntegration {
	        def subflow = messageFlow('sub'){
		     filter(evaluate:{it.class == String})
		    transform(evaluate:{it.toLowerCase()})
		}
			
		mainflow = messageFlow('main') {
		    exec(subflow)
		}

MessageFlows may also be nested:
        
        messageFlow {
	        handle( action:{payload -> payload.toUpperCase()})
		messageFlow {
		    transform(evaluate:{it*2})
		    messageFlow {
			transform(evaluate:{payload->payload.toLowerCase()})
	       }
            }
	}

# Routers

A simple example of routing:

        doWithSpringIntegration {
	    //Must return String, String[] etc...
	    route('myRouter', evaluate: { it == "Hello" ? 'upper.inputChannel' : 'lower.inputChannel' } )	
	    handle('upper', action:{payload -> payload.toUpperCase()})
	    handle('lower', action:{payload -> payload.toLowerCase()})
        }

The above example uses named Service Activators and the channel naming convention to route to the appropriate channel. Note that closures obviate the need for Header Value Router, Payload Type Router, Exception Type Router and Recipient Type Router. These may all be accomplished with the same construct. 

## Recipient List Router

This can be acomplished by simply returning a list of channel names from the closure

	 def count = 0
         def integrationContext = builder.doWithSpringIntegration {
		route('myRouter', evaluate: { ['upper.inputChannel' , 'lower.inputChannel'] } )
		handle('upper', action:{count ++; null})
		handle('lower', action:{count ++; null})
	}
		 
	integrationContext.send('myRouter.inputChannel',"Hello") 
	assert count == 2

## Channel Maps

This example shows the use of the map() method to create a channel map for a router. The messages headers are passed to the closure and the value of the 'foo' header is the key to the channel map.
        messageFlow {
		route('myRouter',evaluate: { Map headers -> headers.foo }) {
			map(bar:'barChannel',baz:'bazChannel')
		}
		transform(inputChannel:'barChannel',evaluate:{it[0..1]},linkToNext:false)
		 
		transform(inputChannel:'bazChannel',evaluate:{it*2})
	}

        def message = MessageBuilder.withPayload("Hello").copyHeaders([foo:'bar']).build()	 
	assert flow.sendAndReceive(message).payload == "He"
		
	message = MessageBuilder.withPayload("SOMETHING").copyHeaders([foo:'baz']).build()
	assert flow.sendAndReceive(message).payload == "SOMETHINGSOMETHING"

Note also the 'linkToNext' attribute can be used to prevent chaining the two transformers within a MessageFlow. Alternatives include creating the transformers external to the MessageFlow or nest each in its own MessageFlow

## Nested Router Conditions

Here's some examples illustrating nested MessageFlows conditionally executed: 

        route(evaluate: { it == "Hello" ? 'foo' : 'bar' } )
	{
		when('foo') {
			handle(action:{payload -> payload.toUpperCase()})
		}

		when('bar') {
			handle(action:{payload -> payload.toLowerCase()})
		}
	}

otherwise() creates a default output channel on the router
        messageFlow {
	        route('myRouter', evaluate: { if (it == "Hello" ) 'foo' } )
		{
			when('foo') {
				handle(action:{payload -> payload.toUpperCase()})
			}

			otherwise {
				handle(action:{payload -> payload.toLowerCase()})
			}
		}
	}

# Native Spring Configuration
As the number of Spring Integration components continuest to grow, it will be difficult for the DSL to keep up to provide first class support. For this reason, it is possible to create XML builder markup directly in the IntegrationBuilder(). 
Note it is also possible to invoke createApplicationContext(ApplicationContext parentContext) to provide additional Spring resources.  

Since the IntegrationBuilder builds an XMLApplicationContext, it is necessary to provide XML namespace declarations. The builder uses convention over configuration to make this easy.

        doWithSpringIntegration {
		namespaces('int-http')
		springXml(defn:{
			'int-http:inbound-channel-adapter'(
			 id:'httpChannelAdapter', 
			channel:'requests',
			  'supported-methods':'PUT, DELETE')
			'si:channel'(id:'requests')
		  })
	}
		
	builder.createApplicationContext()

The namespaces() method takes a comma delimited list of standard Spring namespace prefixes. If a prefix starts with 'int-' it will generate the required XML namespace declarations required for any of the referenced components. Otherwise, it will be interpreted as a Core Spring namespace, e.g., 'jms','jmx','aop'. 

The springXml method, again uses a named attribute, 'defn' for the spring configuration so as not to confuse the builder. The standard namespace for the core Spring Integration components is 'si' and is automatically included. 
    