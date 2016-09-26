/// <reference path="../../typedefs/react/react.d.ts" />
/// <reference path="../slim-bundle.d.ts" />

//------------------------------------------------------------------------------
   namespace debiki2 {
//------------------------------------------------------------------------------

let r = React.DOM;
let FormGroup = rb.FormGroup;
let ControlLabel = rb.ControlLabel;
let FormControl = rb.FormControl;
let HelpBlock = rb.HelpBlock;
let Checkbox = rb.Checkbox;
let Radio = rb.Radio;
let InputGroupAddon = rb.InputGroupAddon;

export var Input = createComponent({
  displayName: 'Input',

  getValue: function() {
    return ReactDOM.findDOMNode(this.refs.theInput)['value'];
  },

  getChecked: function() {
    return ReactDOM.findDOMNode(this.refs.theInput)['checked'];
  },

  render: function() {
    var props = this.props;
    var childProps = _.clone(props);
    childProps.ref = 'theInput';
    delete childProps.label;
    delete childProps.children;
    delete childProps.help;

    if (props.type === 'select' || props.type === 'textarea') {
      childProps.componentClass = props.type;
    }

    let addonBefore = !props.addonBefore ? null : InputGroupAddon({}, props.addonBefore);

    let result;
    let isCheckbox = props.type === 'checkbox';
    let isRadio = props.type === 'radio';
    if (isCheckbox || isRadio) {
      result = (
        r.div({ className: 'form-group' },
          r.div({ className: props.wrapperClassName },
            (isRadio ? Radio : Checkbox).call(null, childProps, props.label),
            r.span({ className: 'help-block' },
              props.help))));
    }
    else if (props.type === 'custom') {
      result = (
        FormGroup({},
          props.label && ControlLabel({ className: props.labelClassName }, props.label),
          r.div({ className: props.wrapperClassName },
            props.children)));
    }
    else {
      result = (
        FormGroup({},
          props.label && ControlLabel({ className: props.labelClassName }, props.label),
          r.div({ className: props.wrapperClassName },
            r.div({ className: 'input-group' },
              addonBefore,
              FormControl(childProps, props.children)),
            props.help && HelpBlock({}, props.help))));
    }

    return result;
  }
});


//------------------------------------------------------------------------------
   }
//------------------------------------------------------------------------------
// vim: fdm=marker et ts=2 sw=2 tw=0 fo=r list