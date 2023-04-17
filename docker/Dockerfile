#
# Building a Docker Image with
# the Latest Ubuntu Version and
# Basic Python Install
# 
# Python for Algorithmic Trading
# (c) Dr. Yves J. Hilpisch
# The Python Quants GmbH
#

# latest Ubuntu version
FROM ubuntu:latest  

# information about maintainer
MAINTAINER skelt

# add the bash script
ADD install.sh /
# change rights for the script
RUN chmod u+x /install.sh
# run the bash script
RUN /install.sh
# prepend the new path
ENV PATH /root/miniconda3/bin:$PATH

#install IB API
COPY IBJts /IBJts
WORKDIR "/IBJts/source/pythonclient"
RUN python setup.py install
WORKDIR "/py4at"

# execute IPython when container is run
EXPOSE 8888
ENTRYPOINT ["jupyter", "lab","--ip=0.0.0.0","--allow-root"]

#CMD ["ipython"]

